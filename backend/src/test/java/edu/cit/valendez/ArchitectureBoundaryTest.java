package edu.cit.valendez;

import edu.cit.valendez.inventory.InventoryService;
import edu.cit.valendez.shop.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureBoundaryTest {

    @Test
    @DisplayName("InventoryService interface must be public")
    void testInventoryServiceInterfaceIsPublic() {
        Class<?> serviceInterface = InventoryService.class;
        assertTrue(serviceInterface.isInterface(), "InventoryService must be an interface");
        assertTrue(Modifier.isPublic(serviceInterface.getModifiers()), "InventoryService interface must be public");
    }

    @Test
    @DisplayName("InventoryServiceImpl must be package-private to enforce modular boundary")
    void testInventoryServiceImplIsPackagePrivate() throws ClassNotFoundException {
        Class<?> implClass = Class.forName("edu.cit.valendez.inventory.InventoryServiceImpl");
        int modifiers = implClass.getModifiers();

        assertFalse(Modifier.isPublic(modifiers), "InventoryServiceImpl MUST NOT be public!");
        assertFalse(Modifier.isProtected(modifiers), "InventoryServiceImpl MUST NOT be protected!");
        assertFalse(Modifier.isPrivate(modifiers), "InventoryServiceImpl MUST NOT be private!");
        // If neither public, protected, nor private is set, it is package-private.
        assertTrue(implClass.isAnnotationPresent(org.springframework.stereotype.Service.class),
                "InventoryServiceImpl should be annotated with @Service for Spring component scanning");
    }

    @Test
    @DisplayName("OrderService must depend ONLY on InventoryService interface, not implementation")
    void testOrderServiceDependsOnlyOnInterface() {
        for (Constructor<?> constructor : OrderService.class.getDeclaredConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                assertNotEquals("edu.cit.valendez.inventory.InventoryServiceImpl", paramType.getName(),
                        "OrderService constructor must NOT depend on InventoryServiceImpl!");
            }
        }

        for (Field field : OrderService.class.getDeclaredFields()) {
            assertNotEquals("edu.cit.valendez.inventory.InventoryServiceImpl", field.getType().getName(),
                    "OrderService fields must NOT reference InventoryServiceImpl!");
        }
    }
}

