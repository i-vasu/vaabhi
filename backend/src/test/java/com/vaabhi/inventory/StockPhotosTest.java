package com.vaabhi.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaabhi.shared.storage.ObjectStorage;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

class StockPhotosTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final StockPhotos photos = new StockPhotos(mock(JdbcClient.class), storage, inventory);

    @BeforeEach
    void setUp() {
        when(storage.configured()).thenReturn(true);
        when(storage.exists(anyString())).thenReturn(true);
        when(inventory.get(1L)).thenReturn(item(0));
    }

    @Test
    void rejectsKeyNotIssuedForStock() {
        assertThatThrownBy(() -> photos.add(1L, new StockPhotos.PhotoForm("tailoring/cloth/" + UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Upload the photo again");
    }

    @Test
    void rejectsMissingFile() {
        when(storage.exists(anyString())).thenReturn(false);
        assertThatThrownBy(() -> photos.add(1L, new StockPhotos.PhotoForm("stock/" + UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Upload the photo again");
    }

    @Test
    void rejectsSeventhPhoto() {
        when(inventory.get(1L)).thenReturn(item(6));
        assertThatThrownBy(() -> photos.add(1L, new StockPhotos.PhotoForm("stock/" + UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Up to 6 photos");
    }

    @Test
    void startUploadNeedsStorage() {
        when(storage.configured()).thenReturn(false);
        assertThatThrownBy(() -> photos.startUpload(1L)).isInstanceOf(ErrorResponseException.class);
    }

    private static StockItem item(int photoCount) {
        return new StockItem(
                1L,
                "Kalamkari",
                null,
                null,
                StockItem.Unit.M,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                false,
                BigDecimal.valueOf(100),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LongStream.range(0, photoCount)
                        .mapToObj(i -> new StockItem.StockPhoto(i, "stock/" + UUID.randomUUID()))
                        .toList());
    }
}
