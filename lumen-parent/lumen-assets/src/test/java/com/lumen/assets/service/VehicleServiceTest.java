package com.lumen.assets.service;

import com.lumen.assets.entity.AstAsset;
import com.lumen.assets.entity.AstVehicle;
import com.lumen.assets.mapper.AstAssetMapper;
import com.lumen.assets.mapper.AstVehicleMapper;
import com.lumen.common.core.exception.ServiceException;
import com.lumen.common.security.context.UserContext;
import com.lumen.common.security.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Vehicle service: mileage monotonic increase (#9), schedule maintenance。
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private AstVehicleMapper vehicleMapper;
    @Mock private AstAssetMapper assetMapper;
    @InjectMocks private VehicleService vehicleService;

    private static final long TENANT = 1L;
    private static final long USER = 10L;
    private static final long VEHICLE_ID = 900L;
    private static final long ASSET_ID = 100L;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
            .userId(USER).tenantId(TENANT).userName("alice")
            .roles(java.util.Set.of("assets_admin")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private AstVehicle vehicle(long mileage) {
        AstVehicle v = new AstVehicle();
        v.setId(VEHICLE_ID);
        v.setTenantId(TENANT);
        v.setAssetId(ASSET_ID);
        v.setPlateNo("京A12345");
        v.setCurrentMileage(mileage);
        return v;
    }

    // 1. recordMileage 单调递增：成功
    @Test
    void recordMileage_increase_ok() {
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle(10000L));
        AstVehicle out = vehicleService.recordMileage(VEHICLE_ID, 12000L, LocalDate.now());
        assertEquals(12000L, out.getCurrentMileage());
    }

    // 2. recordMileage 减小 → 409
    @Test
    void recordMileage_decrease_rejected() {
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle(12000L));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> vehicleService.recordMileage(VEHICLE_ID, 11000L, LocalDate.now()));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("monotonically increasing"));
    }

    // 3. recordMileage 等于当前值 → OK
    @Test
    void recordMileage_equal_ok() {
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle(12000L));
        AstVehicle out = vehicleService.recordMileage(VEHICLE_ID, 12000L, LocalDate.now());
        assertEquals(12000L, out.getCurrentMileage());
    }

    // 4. recordMileage 负数 → 400
    @Test
    void recordMileage_negative_rejected() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> vehicleService.recordMileage(VEHICLE_ID, -1L, LocalDate.now()));
        assertEquals(400, ex.getCode());
    }

    // 5. scheduleMaintenance 设置 nextMaintenanceMileage
    @Test
    void scheduleMaintenance_setsMileage() {
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle(10000L));
        AstVehicle out = vehicleService.scheduleMaintenance(VEHICLE_ID, 15000L);
        assertEquals(15000L, out.getNextMaintenanceMileage());
    }

    // 6. findByPlate 找不到 → 404
    @Test
    void findByPlate_notFound_returns404() {
        when(vehicleMapper.findByPlate("京Z00000")).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> vehicleService.findByPlate("京Z00000"));
        assertEquals(404, ex.getCode());
    }

    // 7. 跨租户 getById → 404
    @Test
    void getById_crossTenant_returns404() {
        AstVehicle v = vehicle(1000L);
        v.setTenantId(99L);
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(v);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> vehicleService.getById(VEHICLE_ID));
        assertEquals(404, ex.getCode());
    }
}