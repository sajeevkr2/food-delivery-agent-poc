import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatResponse {
  reply: string;
}

export interface OrderItemView {
  itemName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface CancelResponse {
  cancelled: boolean;
  message: string;
}

export interface OrderView {
  id: string;
  restaurantName: string;
  items: OrderItemView[];
  total: number;
  address: string;
  paymentMethod: string;
  status: 'CONFIRMED' | 'PREPARING' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'CANCELLED';
  placedAt: string;
}

@Injectable({ providedIn: 'root' })
export class FoodDeliveryService {

  constructor(private http: HttpClient) {}

  sendMessage(message: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>('/api/chat', { message });
  }

  resetChat(): Observable<void> {
    return this.http.delete<void>('/api/chat');
  }

  cancelOrder(id: string): Observable<CancelResponse> {
    return this.http.delete<CancelResponse>(`/api/orders/${id}`);
  }

  getOrder(id: string): Observable<OrderView> {
    return this.http.get<OrderView>(`/api/orders/${id}`);
  }
}
