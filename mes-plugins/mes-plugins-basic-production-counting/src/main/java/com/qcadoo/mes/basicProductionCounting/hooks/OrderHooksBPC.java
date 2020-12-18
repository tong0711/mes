/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.basicProductionCounting.hooks;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basicProductionCounting.BasicProductionCountingService;
import com.qcadoo.mes.basicProductionCounting.constants.OrderFieldsBPC;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.states.constants.OrderStateStringValues;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderHooksBPC {

    @Autowired
    private BasicProductionCountingService basicProductionCountingService;

    public void onSave(final DataDefinition orderDD, final Entity order) {
        if (Objects.nonNull(order.getBelongsToField(OrderFields.TECHNOLOGY))) {
            boolean shouldCreateProductionCounting = checkIfShouldCreateProductionCounting(order);
            if (shouldCreateProductionCounting) {
                boolean productToProductGroupTechnologyDoesntExists = basicProductionCountingService
                        .createProductionCounting(order);
                if (productToProductGroupTechnologyDoesntExists) {
                    order.addGlobalMessage("basicProductionCounting.productionCountingQuantity.error.productToProductGroupTechnologyDoesntExists");
                }
            } else if (checkIfShouldReCreateProductionCounting(order)) {
                for (Entity pcq : order.getHasManyField(OrderFieldsBPC.PRODUCTION_COUNTING_QUANTITIES)) {
                    pcq.getDataDefinition().delete(pcq.getId());
                }
                for (Entity bpc : order.getHasManyField(OrderFieldsBPC.BASIC_PRODUCTION_COUNTINGS)) {
                    bpc.getDataDefinition().delete(bpc.getId());
                }
                for (Entity pqor : order.getHasManyField(OrderFieldsBPC.PRODUCTION_COUNTING_OPERATION_RUNS)) {
                    pqor.getDataDefinition().delete(pqor.getId());
                }
                order.setField(OrderFieldsBPC.BASIC_PRODUCTION_COUNTINGS, Lists.newArrayList());
                order.setField(OrderFieldsBPC.PRODUCTION_COUNTING_OPERATION_RUNS, Lists.newArrayList());
                order.setField(OrderFieldsBPC.PRODUCTION_COUNTING_QUANTITIES, Lists.newArrayList());
                boolean productToProductGroupTechnologyDoesntExists = basicProductionCountingService
                        .createProductionCounting(order);
                if (productToProductGroupTechnologyDoesntExists) {
                    order.addGlobalMessage("basicProductionCounting.productionCountingQuantity.error.productToProductGroupTechnologyDoesntExists");
                }
            } else {

                updateProductionCountingQuantitiesAndOperationRuns(order);
                updateProducedQuantity(order);
            }
        } else if (Objects.nonNull(order.getId())) {
            order.setField(OrderFieldsBPC.BASIC_PRODUCTION_COUNTINGS, Lists.newArrayList());
            order.setField(OrderFieldsBPC.PRODUCTION_COUNTING_OPERATION_RUNS, Lists.newArrayList());
            order.setField(OrderFieldsBPC.PRODUCTION_COUNTING_QUANTITIES, Lists.newArrayList());
        }
    }

    private boolean checkIfShouldReCreateProductionCounting(Entity order) {
        Entity technology = order.getBelongsToField(OrderFields.TECHNOLOGY);
        Entity technologyDb = order.getDataDefinition().get(order.getId()).getBelongsToField(OrderFields.TECHNOLOGY);
        if (Objects.isNull(technologyDb) || !technology.getId().equals(technologyDb.getId())) {
            return true;
        }
        return false;
    }

    private boolean checkIfShouldCreateProductionCounting(final Entity order) {
        return isProductionCountingGenerated(order);

    }

    private boolean isProductionCountingGenerated(Entity order) {
        return order.getHasManyField(OrderFieldsBPC.BASIC_PRODUCTION_COUNTINGS).isEmpty();
    }

    private void updateProductionCountingQuantitiesAndOperationRuns(final Entity order) {
        BigDecimal plannedQuantity = order.getDecimalField(OrderFields.PLANNED_QUANTITY);

        String state = order.getStringField(OrderFields.STATE);

        if (checkOrderState(state) && hasPlannedQuantityChanged(order, plannedQuantity)) {
            basicProductionCountingService.updateProductionCountingQuantitiesAndOperationRuns(order);
        }
    }

    private boolean checkOrderState(final String state) {
        return OrderStateStringValues.PENDING.equals(state) || OrderStateStringValues.ACCEPTED.equals(state)
                || OrderStateStringValues.IN_PROGRESS.equals(state) || OrderStateStringValues.INTERRUPTED.equals(state);
    }

    private void updateProducedQuantity(Entity order) {
        String state = order.getStringField(OrderFields.STATE);

        if (checkOrderState(state)) {
            basicProductionCountingService.updateProducedQuantity(order);
        }
    }

    private boolean hasPlannedQuantityChanged(final Entity order, final BigDecimal plannedQuantity) {
        Entity existingOrder = getExistingOrder(order);

        if (existingOrder == null) {
            return false;
        }

        BigDecimal existingOrderPlannedQuantity = existingOrder.getDecimalField(OrderFields.PLANNED_QUANTITY);

        if (existingOrderPlannedQuantity == null) {
            return true;
        }

        return !BigDecimalUtils.valueEquals(existingOrderPlannedQuantity, plannedQuantity);
    }

    private Entity getExistingOrder(final Entity order) {
        if (order.getId() == null) {
            return null;
        }

        StringBuilder query = new StringBuilder();

        query.append("SELECT ord.id AS id, ord.plannedQuantity AS plannedQuantity ");
        query.append("FROM #orders_order ord WHERE id = :id");

        Entity orderDB = order.getDataDefinition().find(query.toString()).setLong("id", order.getId()).setMaxResults(1)
                .uniqueResult();

        return orderDB;
    }

}
