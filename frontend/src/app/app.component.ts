import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FoodDeliveryService, OrderView } from './food-delivery.service';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnDestroy {
  title = 'Food Delivery Agent';

  messages: ChatMessage[] = [
    { role: 'assistant', text: 'Hi! Tell me what you\'re craving and I\'ll find something for you — e.g. "something spicy and vegetarian under 300".' }
  ];
  draft = '';
  sending = false;
  cancelling = false;

  activeOrder: OrderView | null = null;
  private orderPollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(private foodDeliveryService: FoodDeliveryService) {}

  ngOnDestroy(): void {
    if (this.orderPollHandle) {
      clearInterval(this.orderPollHandle);
    }
  }

  send(): void {
    const message = this.draft.trim();
    if (!message || this.sending) {
      return;
    }
    this.messages.push({ role: 'user', text: message });
    this.draft = '';
    this.sending = true;

    this.foodDeliveryService.sendMessage(message).subscribe({
      next: (res) => {
        this.messages.push({ role: 'assistant', text: res.reply });
        this.sending = false;
        this.detectOrderId(res.reply);
      },
      error: () => {
        this.messages.push({ role: 'assistant', text: 'Sorry, something went wrong talking to the backend. Is Ollama running?' });
        this.sending = false;
      }
    });
  }

  resetChat(): void {
    this.foodDeliveryService.resetChat().subscribe(() => {
      this.messages = [{ role: 'assistant', text: 'Conversation reset. What would you like to eat?' }];
      this.activeOrder = null;
      if (this.orderPollHandle) {
        clearInterval(this.orderPollHandle);
        this.orderPollHandle = null;
      }
    });
  }

  cancelOrder(): void {
    if (!this.activeOrder || this.cancelling) {
      return;
    }
    const orderId = this.activeOrder.id;
    this.cancelling = true;
    this.foodDeliveryService.cancelOrder(orderId).subscribe({
      next: (res) => {
        this.messages.push({ role: 'assistant', text: res.message });
        this.clearOrder();
        this.cancelling = false;
      },
      // 409 = too late to cancel (or unknown order); the server explains why.
      error: (err) => {
        this.messages.push({ role: 'assistant', text: err?.error?.message ?? 'Could not cancel the order.' });
        this.cancelling = false;
      }
    });
  }

  private clearOrder(): void {
    this.activeOrder = null;
    if (this.orderPollHandle) {
      clearInterval(this.orderPollHandle);
      this.orderPollHandle = null;
    }
  }

  private detectOrderId(reply: string): void {
    const match = reply.match(/ORD\d+/);
    if (!match) {
      return;
    }
    const orderId = match[0];
    if (this.orderPollHandle) {
      clearInterval(this.orderPollHandle);
    }
    this.pollOrder(orderId);
    this.orderPollHandle = setInterval(() => this.pollOrder(orderId), 5000);
  }

  private pollOrder(orderId: string): void {
    this.foodDeliveryService.getOrder(orderId).subscribe({
      next: (order) => {
        // Cancelled (e.g. by asking in chat): the order is gone from the UI.
        if (order.status === 'CANCELLED') {
          this.clearOrder();
          return;
        }
        this.activeOrder = order;
        if (order.status === 'DELIVERED' && this.orderPollHandle) {
          clearInterval(this.orderPollHandle);
          this.orderPollHandle = null;
        }
      },
      // The order ID in a chat reply may not exist (the model can invent one);
      // don't show a card for it and stop polling.
      error: () => {
        this.activeOrder = null;
        if (this.orderPollHandle) {
          clearInterval(this.orderPollHandle);
          this.orderPollHandle = null;
        }
      }
    });
  }
}
