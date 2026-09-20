package com.poc.fooddelivery.mcpserver.data;

import com.poc.fooddelivery.mcpserver.model.MenuItem;
import com.poc.fooddelivery.mcpserver.model.Restaurant;

import java.util.List;

/**
 * Stand-in for a real Zomato/Swiggy catalog integration, which requires
 * partner onboarding and isn't available for self-serve/POC use.
 */
public final class MockDataStore {

    public static final List<Restaurant> RESTAURANTS = List.of(
            new Restaurant("r1", "Spice Route", "Indian", 4.3, List.of(
                    new MenuItem("i1", "Paneer Tikka", 249, true, true, "Grilled cottage cheese with spices"),
                    new MenuItem("i2", "Butter Chicken", 349, false, false, "Creamy tomato chicken curry"),
                    new MenuItem("i3", "Dal Makhani", 199, true, false, "Slow-cooked black lentils"),
                    new MenuItem("i4", "Chicken Vindaloo", 379, false, true, "Fiery Goan-style chicken curry"),
                    new MenuItem("i17", "Naan", 50, true, false, "Soft tandoor-baked bread")
            )),
            new Restaurant("r2", "Dragon Wok", "Chinese", 4.1, List.of(
                    new MenuItem("i5", "Veg Manchurian", 229, true, true, "Fried vegetable balls in spicy sauce"),
                    new MenuItem("i6", "Kung Pao Chicken", 329, false, true, "Spicy stir-fried chicken with peanuts"),
                    new MenuItem("i7", "Vegetable Fried Rice", 189, true, false, "Wok-tossed rice with vegetables"),
                    new MenuItem("i8", "Sweet and Sour Pork", 359, false, false, "Tangy pork stir fry")
            )),
            new Restaurant("r3", "Pizza Bella", "Italian", 4.5, List.of(
                    new MenuItem("i9", "Margherita Pizza", 279, true, false, "Classic tomato and mozzarella"),
                    new MenuItem("i10", "Pepperoni Pizza", 349, false, false, "Loaded with pepperoni"),
                    new MenuItem("i11", "Arrabbiata Pasta", 259, true, true, "Spicy tomato pasta"),
                    new MenuItem("i12", "Garlic Bread", 149, true, false, "Toasted bread with garlic butter")
            )),
            new Restaurant("r4", "Green Bowl", "Healthy", 4.6, List.of(
                    new MenuItem("i13", "Quinoa Salad", 229, true, false, "Quinoa with fresh vegetables"),
                    new MenuItem("i14", "Grilled Tofu Bowl", 259, true, true, "Spicy grilled tofu with greens"),
                    new MenuItem("i15", "Chicken Caesar Salad", 289, false, false, "Grilled chicken over romaine"),
                    new MenuItem("i16", "Spicy Chickpea Wrap", 219, true, true, "Chickpea patty with chili sauce")
            ))
    );

    private MockDataStore() {
    }
}
