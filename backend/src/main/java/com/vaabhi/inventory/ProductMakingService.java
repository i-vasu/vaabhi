package com.vaabhi.inventory;

import com.vaabhi.shared.rupees.Rupees;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traces products back to the stock they were made from and the people who worked on them. Using stock for a
 * product takes it out of stock (an OUT movement noted with the product); removing the link puts it back.
 */
@Service
public class ProductMakingService {

    private final JdbcClient jdbc;
    private final InventoryService inventory;

    ProductMakingService(JdbcClient jdbc, InventoryService inventory) {
        this.jdbc = jdbc;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public ProductMaking get(long productId) {
        productName(productId);
        var materials = jdbc.sql("""
                        SELECT pm.id, i.id AS stock_item_id, i.name AS stock_item_name, i.unit, pm.quantity,
                               ROUND(pm.quantity * i.cost_rupees, 2) AS cost_rupees, i.supplier_name, i.material,
                               i.gsm, i.thread_count, pm.note, pm.created_at
                        FROM product_material pm JOIN stock_item i ON i.id = pm.stock_item_id
                        WHERE pm.product_id = :product
                        ORDER BY pm.created_at, pm.id
                        """)
                .param("product", productId)
                .query(ProductMaking.ProductMaterial.class)
                .list();
        var work = jdbc.sql("""
                        SELECT id, staff_id, worker_name, work_type, cost_rupees, done_on, note
                        FROM product_work WHERE product_id = :product ORDER BY created_at, id
                        """)
                .param("product", productId)
                .query(ProductMaking.ProductWork.class)
                .list();
        var cost = Rupees.sum(materials, ProductMaking.ProductMaterial::costRupees)
                .add(Rupees.sum(work, ProductMaking.ProductWork::costRupees));
        return new ProductMaking(materials, work, cost);
    }

    @Transactional
    public ProductMaking addMaterial(long productId, ProductMaking.ProductMaterialForm form, @Nullable String actor) {
        var name = productName(productId);
        var note = form.note() == null || form.note().isBlank()
                ? "Used for " + name
                : form.note().trim();
        var movement = inventory.move(
                form.stockItemId(),
                new StockMovement.StockMovementForm(StockMovement.Kind.OUT, form.quantity(), null, truncate(note)),
                actor);
        jdbc.sql("""
                        INSERT INTO product_material (product_id, stock_item_id, quantity, movement_id, note, created_by)
                        VALUES (:product, :item, :quantity, :movement, :note, :by)
                        """)
                .param("product", productId)
                .param("item", form.stockItemId())
                .param("quantity", form.quantity())
                .param("movement", movement.id())
                .param("note", InventoryService.blankToNull(form.note()))
                .param("by", actor)
                .update();
        return get(productId);
    }

    /** Removes the link and the stock movement it made, so the quantity is back in stock. */
    @Transactional
    public ProductMaking removeMaterial(long productId, long materialId) {
        var movementId = jdbc.sql("""
                        DELETE FROM product_material WHERE id = :id AND product_id = :product RETURNING movement_id
                        """)
                .param("id", materialId)
                .param("product", productId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        jdbc.sql("DELETE FROM stock_movement WHERE id = :id")
                .param("id", movementId)
                .update();
        return get(productId);
    }

    @Transactional
    public ProductMaking addWork(long productId, ProductMaking.ProductWorkForm form) {
        productName(productId);
        if (form.staffId() != null
                && jdbc.sql("SELECT COUNT(*) FROM staff_account WHERE id = :id")
                                .param("id", form.staffId())
                                .query(Long.class)
                                .single()
                        == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That staff member does not exist");
        }
        jdbc.sql("""
                        INSERT INTO product_work (product_id, staff_id, worker_name, work_type, cost_rupees, done_on, note)
                        VALUES (:product, :staff, :worker, :type, :cost, :doneOn, :note)
                        """)
                .param("product", productId)
                .param("staff", form.staffId())
                .param("worker", form.workerName().trim())
                .param("type", form.workType().trim())
                .param("cost", form.costRupees())
                .param("doneOn", form.doneOn())
                .param("note", InventoryService.blankToNull(form.note()))
                .update();
        return get(productId);
    }

    @Transactional
    public ProductMaking removeWork(long productId, long workId) {
        if (jdbc.sql("DELETE FROM product_work WHERE id = :id AND product_id = :product")
                        .param("id", workId)
                        .param("product", productId)
                        .update()
                == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work entry not found");
        }
        return get(productId);
    }

    /** Products made from a stock item, newest first. */
    @Transactional(readOnly = true)
    public List<ProductMaking.StockUsedIn> usedIn(long stockItemId) {
        inventory.get(stockItemId);
        return jdbc.sql("""
                        SELECT p.id AS product_id, p.name AS product_name, p.slug AS product_slug,
                               p.status AS product_status, pm.quantity,
                               pm.created_at
                        FROM product_material pm JOIN product p ON p.id = pm.product_id
                        WHERE pm.stock_item_id = :item
                        ORDER BY pm.created_at DESC, pm.id DESC
                        """)
                .param("item", stockItemId)
                .query(ProductMaking.StockUsedIn.class)
                .list();
    }

    private String productName(long productId) {
        return jdbc.sql("SELECT name FROM product WHERE id = :id")
                .param("id", productId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private static String truncate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
