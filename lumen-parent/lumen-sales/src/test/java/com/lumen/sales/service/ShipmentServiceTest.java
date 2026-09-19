package com.lumen.sales.service;

import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import com.lumen.sales.entity.Shipment;
import com.lumen.sales.mapper.ShipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock private ShipmentMapper shipmentMapper;
    @Mock private CustomerService customerService;
    @InjectMocks private ShipmentService shipmentService;

    private static final long UID = 100L;
    private static final long TENANT = 1L;
    private static final long SHIP_ID = 99L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(UID).tenantId(TENANT).userName("alice")
            .roles(Set.of("sales")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private Shipment stubShipment(String status) {
        Shipment s = new Shipment();
        s.setId(SHIP_ID);
        s.setOrderId(500L);
        s.setStatus(status);
        s.setTenantId(TENANT);
        return s;
    }

    @Test
    void markDelivered_pendingShipment_flipsToDelivered() {
        Shipment s = stubShipment(ShipmentService.STATUS_PENDING);
        when(shipmentMapper.selectById(SHIP_ID)).thenReturn(s);
        when(shipmentMapper.updateById(any(Shipment.class))).thenAnswer(inv -> 1);
        Shipment out = shipmentService.markDelivered(SHIP_ID);
        assertEquals(ShipmentService.STATUS_DELIVERED, out.getStatus());
    }

    @Test
    void markDelivered_alreadyDelivered_returnsSame() {
        Shipment s = stubShipment(ShipmentService.STATUS_DELIVERED);
        when(shipmentMapper.selectById(SHIP_ID)).thenReturn(s);
        Shipment out = shipmentService.markDelivered(SHIP_ID);
        assertEquals(ShipmentService.STATUS_DELIVERED, out.getStatus());
    }

    @Test
    void markDelivered_exceptionShipment_throws409() {
        Shipment s = stubShipment(ShipmentService.STATUS_EXCEPTION);
        when(shipmentMapper.selectById(SHIP_ID)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> shipmentService.markDelivered(SHIP_ID));
        assertEquals(409, ex.getCode());
    }

    @Test
    void markException_pending_flipsToException() {
        Shipment s = stubShipment(ShipmentService.STATUS_PENDING);
        when(shipmentMapper.selectById(SHIP_ID)).thenReturn(s);
        when(shipmentMapper.updateById(any(Shipment.class))).thenAnswer(inv -> 1);
        Shipment out = shipmentService.markException(SHIP_ID, "lost in transit");
        assertEquals(ShipmentService.STATUS_EXCEPTION, out.getStatus());
    }

    @Test
    void markException_delivered_throws409() {
        Shipment s = stubShipment(ShipmentService.STATUS_DELIVERED);
        when(shipmentMapper.selectById(SHIP_ID)).thenReturn(s);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> shipmentService.markException(SHIP_ID, null));
        assertEquals(409, ex.getCode());
    }
}