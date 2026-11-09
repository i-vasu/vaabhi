package com.vaabhi.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaabhi.shared.storage.ObjectStorage;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

class StockPhotosTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final StatementSpec spec = mock(StatementSpec.class, Answers.RETURNS_SELF);
    private final StockPhotos photos = new StockPhotos(jdbc, storage, inventory);

    @BeforeEach
    void setUp() {
        when(storage.configured()).thenReturn(true);
        when(storage.exists(anyString())).thenReturn(true);
        when(inventory.get(1L)).thenReturn(item(0));
        when(jdbc.sql(anyString())).thenReturn(spec);
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
    void rejectsKeyThatIsNotAUuid() {
        assertThatThrownBy(() -> photos.add(1L, new StockPhotos.PhotoForm("stock/not-a-uuid")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Upload the photo again");
        verify(storage, never()).exists(anyString());
    }

    @Test
    void addSavesTheUploadedPhotoAtTheEnd() {
        when(inventory.get(1L)).thenReturn(item(StockPhotos.MAX - 1));
        var key = "stock/" + UUID.randomUUID();

        photos.add(1L, new StockPhotos.PhotoForm(" " + key + " "));

        verify(storage).exists(key + "/400.webp");
        verify(spec).param("item", 1L);
        verify(spec).param("key", key);
        verify(spec).update();
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

    @Test
    void startUploadIssuesAPresignedPutForANewKey() {
        var url = URI.create("https://r2.example.test/put");
        when(storage.presignPut(anyString(), any(Duration.class))).thenReturn(url);

        var upload = photos.startUpload(1L);

        assertThat(upload.key()).matches("stock/[0-9a-f-]{36}");
        assertThat(upload.uploadUrl()).isEqualTo(url.toString());
        assertThat(upload.expiresAt()).isAfter(Instant.now());
        verify(storage).presignPut(upload.key() + "/400.webp", Duration.ofMinutes(15));
    }

    @Test
    void removeTakesThePhotoOffTheItem() {
        when(spec.update()).thenReturn(1);

        assertThat(photos.remove(1L, 9L)).isEqualTo(item(0));
        verify(spec).param("id", 9L);
        verify(spec).param("item", 1L);
    }

    @Test
    void removeRejectsAPhotoNotOnThisItem() {
        when(spec.update()).thenReturn(0);
        assertThatThrownBy(() -> photos.remove(1L, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Photo not found");
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
