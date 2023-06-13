package com.qcadoo.mes.productionCounting.xls;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.qcadoo.mes.productionCounting.xls.dto.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.mes.basic.constants.CurrencyFields;
import com.qcadoo.mes.basic.util.CurrencyService;
import com.qcadoo.mes.productionCounting.constants.OrderBalanceFields;
import com.qcadoo.mes.productionCounting.constants.ProductionBalanceFields;
import com.qcadoo.mes.productionCounting.constants.ProductionCountingConstants;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.report.api.xls.XlsDocumentService;

@Service
public class ProductionBalanceXlsService extends XlsDocumentService {

    @Autowired
    private TranslationService translationService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private ProductionBalanceRepository productionBalanceRepository;

    @Autowired
    private CurrencyService currencyService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    private static final List<String> PRODUCED_QUANTITIES_HEADERS = Lists.newArrayList("orderNumber", "productNumber",
            "productName", "additionalFinalProducts", "plannedQuantity", "producedQuantity", "wastesQuantity", "additionalFinalProductsQuantity", "producedWastes", "deviation", "productUnit");

    private static final List<String> ORDER_PRODUCTS_HEADERS = Lists.newArrayList("orderNumber", "productType", "productNumber",
            "productName", "plannedQuantity", "producedQuantity", "deviation", "productUnit");

    @Override
    protected void addHeader(HSSFSheet sheet, Locale locale, Entity entity) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        HSSFRow headerRow = sheet.createRow(0);
        int columnIndex = 0;
        for (String key : PRODUCED_QUANTITIES_HEADERS) {
            createHeaderCell(stylesContainer, headerRow,
                    translationService.translate("productionCounting.productionBalance.report.xls.header." + key, locale),
                    columnIndex);
            columnIndex++;
        }
    }

    @Override
    protected void addSeries(HSSFSheet sheet, Entity entity) {
        List<Long> ordersIds = getOrdersIds(entity);

        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        createProducedQuantitiesSheet(sheet, ordersIds, stylesContainer);
    }

    @Override
    public String getReportTitle(Locale locale) {
        return translationService.translate("productionCounting.productionBalance.report.xls.sheet.producedQuantities", locale);
    }

    @Override
    protected void addExtraSheets(final HSSFWorkbook workbook, Entity entity, Locale locale) {
        List<Long> ordersIds = getOrdersIds(entity);

        List<OrderProduct> orderProducts = productionBalanceRepository.getOrderProducts(ordersIds);
        createOrderProductsSheet(orderProducts, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.orderProducts", locale)),
                locale);

        List<MaterialCost> materialCosts = productionBalanceRepository.getMaterialCosts(entity, ordersIds);
        recalculateMaterialCostsWithCurrencies(materialCosts);
        createMaterialCostsSheet(materialCosts, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.materialCosts", locale)),
                locale);
        createLaborTimeSheet(createSheet(workbook, translationService.translate(LaborTimeSheetConstants.SHEET_TITLE, locale)),
                ordersIds, locale);

        List<LaborTimeDetails> laborTimeDetailsList = productionBalanceRepository.getLaborTimeDetails(entity, ordersIds);
        createLaborTimeDetailsSheet(laborTimeDetailsList, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails", locale)),
                locale);
        createPieceworkSheet(createSheet(workbook, translationService.translate(PieceworkSheetConstants.SHEET_TITLE, locale)),
                ordersIds, locale);
        createStoppagesSheet(
                createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages", locale)),
                ordersIds, locale);

        List<ProductionCost> productionCosts = productionBalanceRepository.getProductionCosts(entity, ordersIds);
        createProductionCostsSheet(productionCosts, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.productionCosts", locale)),
                locale);

        List<AdditionalCost> additionalCosts = productionBalanceRepository.getAdditionalCosts(ordersIds);
        createAdditionalCostsSheet(additionalCosts, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.additionalCosts", locale)),
                locale);

        List<OrderBalance> ordersBalance = productionBalanceRepository.getOrdersBalance(entity, ordersIds, materialCosts,
                productionCosts);
        saveOrdersBalance(entity, ordersBalance);
        createOrdersBalanceSheet(ordersBalance, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance", locale)),
                locale);

        List<OrderBalance> componentsBalance = productionBalanceRepository.getComponentsBalance(entity, ordersIds, ordersBalance);
        createOrdersBalanceSheet(componentsBalance, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.componentsBalance", locale)),
                locale);

        List<OrderBalance> productsBalance = productionBalanceRepository.getProductsBalance(entity, ordersIds, componentsBalance);
        createProductsBalanceSheet(productsBalance, createSheet(workbook,
                        translationService.translate("productionCounting.productionBalance.report.xls.sheet.productsBalance", locale)),
                locale);
    }

    private void createOrderProductsSheet(List<OrderProduct> orderProducts, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        HSSFRow headerRow = sheet.createRow(0);
        int columnIndex = 0;
        for (String key : ORDER_PRODUCTS_HEADERS) {
            createHeaderCell(stylesContainer, headerRow,
                    translationService.translate("productionCounting.productionBalance.report.xls.sheet.orderProducts." + key, locale),
                    columnIndex);
            columnIndex++;
        }

        int rowIndex = 1;
        for (OrderProduct op : orderProducts) {
            HSSFRow row = sheet.createRow(rowIndex);
            createRegularCell(stylesContainer, row, 0, op.getOrderNumber());
            if (StringUtils.isNotEmpty(op.getProductType())) {
                createRegularCell(stylesContainer, row, 1, translationService.translate("basicProductionCounting.productionCountingQuantity.typeOfMaterial.value." + op.getProductType(), locale));
            } else {
                createRegularCell(stylesContainer, row, 1, "");
            }
            createRegularCell(stylesContainer, row, 2, op.getProductNumber());
            createRegularCell(stylesContainer, row, 3, op.getProductName());
            createNumericCell(stylesContainer, row, 4, op.getPlannedQuantity(), false);
            createNumericCell(stylesContainer, row, 5, op.getProducedQuantity(), true);
            createNumericCell(stylesContainer, row, 6, op.getDeviation(), false);
            createRegularCell(stylesContainer, row, 7, op.getProductUnit());
            rowIndex++;
        }

        for (int i = 0; i <= 7; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void saveOrdersBalance(Entity productionBalance, List<OrderBalance> ordersBalance) {
        DataDefinition orderBalanceDD = dataDefinitionService.get(ProductionCountingConstants.PLUGIN_IDENTIFIER,
                ProductionCountingConstants.MODEL_ORDER_BALANCE);

        for (OrderBalance oBalance : ordersBalance) {
            Entity orderBalance = orderBalanceDD.create();

            orderBalance.setField(OrderBalanceFields.PRODUCTION_BALANCE, productionBalance);
            orderBalance.setField(OrderBalanceFields.ORDER_NUMBER, oBalance.getOrderNumber());
            orderBalance.setField(OrderBalanceFields.PRODUCT_NUMBER, oBalance.getProductNumber());
            orderBalance.setField(OrderBalanceFields.PRODUCT_NAME, oBalance.getProductName());
            orderBalance.setField(OrderBalanceFields.PLANNED_QUANTITY,
                    numberService.setScaleWithDefaultMathContext(oBalance.getPlannedQuantity(), 2));
            orderBalance.setField(OrderBalanceFields.PRODUCED_QUANTITY,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProducedQuantity(), 2));
            orderBalance.setField(OrderBalanceFields.DEVIATION,
                    numberService.setScaleWithDefaultMathContext(oBalance.getDeviation(), 2));
            orderBalance.setField(OrderBalanceFields.PRODUCT_UNIT, oBalance.getProductUnit());
            orderBalance.setField(OrderBalanceFields.PLANNED_MATERIAL_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getPlannedMaterialCosts(), 2));
            orderBalance.setField(OrderBalanceFields.MATERIAL_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getMaterialCosts(), 2));
            orderBalance.setField(OrderBalanceFields.MATERIAL_COSTS_DEVIATION,
                    numberService.setScaleWithDefaultMathContext(oBalance.getMaterialCostsDeviation(), 2));
            orderBalance.setField(OrderBalanceFields.PLANNED_PRODUCTION_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getPlannedProductionCosts(), 2));
            orderBalance.setField(OrderBalanceFields.PRODUCTION_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProductionCosts(), 2));
            orderBalance.setField(OrderBalanceFields.PRODUCTION_COSTS_DEVIATION,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProductionCostsDeviation(), 2));
            orderBalance.setField(OrderBalanceFields.TECHNICAL_PRODUCTION_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getTechnicalProductionCosts(), 2));
            orderBalance.setField(OrderBalanceFields.MATERIAL_COST_MARGIN,
                    numberService.setScaleWithDefaultMathContext(oBalance.getMaterialCostMargin(), 2));
            orderBalance.setField(OrderBalanceFields.MATERIAL_COST_MARGIN_VALUE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getMaterialCostMarginValue(), 2));
            orderBalance.setField(OrderBalanceFields.PRODUCTION_COST_MARGIN,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProductionCostMargin(), 2));
            orderBalance.setField(OrderBalanceFields.PRODUCTION_COST_MARGIN_VALUE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProductionCostMarginValue(), 2));
            orderBalance.setField(OrderBalanceFields.ADDITIONAL_OVERHEAD,
                    numberService.setScaleWithDefaultMathContext(oBalance.getAdditionalOverhead(), 2));
            orderBalance.setField(OrderBalanceFields.DIRECT_ADDITIONAL_COST,
                    numberService.setScaleWithDefaultMathContext(oBalance.getDirectAdditionalCost(), 2));
            orderBalance.setField(OrderBalanceFields.TOTAL_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getTotalCosts(), 2));
            orderBalance.setField(OrderBalanceFields.REGISTRATION_PRICE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getRegistrationPrice(), 2));
            orderBalance.setField(OrderBalanceFields.REGISTRATION_PRICE_OVERHEAD,
                    numberService.setScaleWithDefaultMathContext(oBalance.getRegistrationPriceOverhead(), 2));
            orderBalance.setField(OrderBalanceFields.REGISTRATION_PRICE_OVERHEAD_VALUE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getRegistrationPriceOverheadValue(), 2));
            orderBalance.setField(OrderBalanceFields.REAL_PRODUCTION_COSTS,
                    numberService.setScaleWithDefaultMathContext(oBalance.getRealProductionCosts(), 2));
            orderBalance.setField(OrderBalanceFields.TECHNICAL_PRODUCTION_COST_OVERHEAD,
                    numberService.setScaleWithDefaultMathContext(oBalance.getTechnicalProductionCostOverhead(), 2));
            orderBalance.setField(OrderBalanceFields.TECHNICAL_PRODUCTION_COST_OVERHEAD_VALUE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getTechnicalProductionCostOverheadValue(), 2));
            orderBalance.setField(OrderBalanceFields.TOTAL_MANUFACTURING_COST,
                    numberService.setScaleWithDefaultMathContext(oBalance.getTotalManufacturingCost(), 2));
            orderBalance.setField(OrderBalanceFields.PROFIT,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProfit(), 2));
            orderBalance.setField(OrderBalanceFields.PROFIT_VALUE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getProfitValue(), 2));
            orderBalance.setField(OrderBalanceFields.SELL_PRICE,
                    numberService.setScaleWithDefaultMathContext(oBalance.getSellPrice(), 2));
            orderBalanceDD.save(orderBalance);
        }
    }

    private void recalculateMaterialCostsWithCurrencies(List<MaterialCost> materialCosts) {
        Entity currency = currencyService.getCurrentCurrency();
        for (MaterialCost materialCost : materialCosts) {
            if (currency != null && materialCost.getCostCurrencyId() != null
                    && !currency.getId().equals(materialCost.getCostCurrencyId())) {
                if (CurrencyService.PLN.equals(currency.getStringField(CurrencyFields.ALPHABETIC_CODE))) {
                    Entity materialCurrency = currencyService.getCurrencyById(materialCost.getCostCurrencyId());
                    materialCost
                            .setPlannedCost(currencyService.getConvertedValue(materialCost.getPlannedCost(), materialCurrency));
                    materialCost.setRealCost(currencyService.getConvertedValue(materialCost.getRealCost(), materialCurrency));
                    materialCost.setValueDeviation(
                            currencyService.getConvertedValue(materialCost.getValueDeviation(), materialCurrency));
                } else {
                    materialCost.setPlannedCost(BigDecimal.ZERO);
                    materialCost.setRealCost(BigDecimal.ZERO);
                    materialCost.setValueDeviation(BigDecimal.ZERO);
                }
            }
        }
    }

    private List<Long> getOrdersIds(final Entity productionBalance) {
        List<Entity> orders = productionBalance.getHasManyField(ProductionBalanceFields.ORDERS);
        return orders.stream().map(Entity::getId).collect(Collectors.toList());
    }

    private void createProducedQuantitiesSheet(HSSFSheet sheet, List<Long> ordersIds, StylesContainer stylesContainer) {
        List<ProducedQuantity> producedQuantities = productionBalanceRepository.getProducedQuantities(ordersIds);
        int rowIndex = 1;
        for (ProducedQuantity producedQuantity : producedQuantities) {
            HSSFRow row = sheet.createRow(rowIndex);
            createRegularCell(stylesContainer, row, 0, producedQuantity.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, producedQuantity.getProductNumber());
            createRegularCell(stylesContainer, row, 2, producedQuantity.getProductName());
            createRegularCell(stylesContainer, row, 3, toYesOrNoFromString(producedQuantity.getAdditionalFinalProducts(), LocaleContextHolder.getLocale()));
            createNumericCell(stylesContainer, row, 4, producedQuantity.getPlannedQuantity(), false);
            createNumericCell(stylesContainer, row, 5, producedQuantity.getProducedQuantity(), true);
            createNumericCell(stylesContainer, row, 6, producedQuantity.getWastesQuantity(), false);
            createNumericCell(stylesContainer, row, 7, producedQuantity.getAdditionalFinalProductsQuantity(), false);
            createNumericCell(stylesContainer, row, 8, producedQuantity.getProducedWastes(), false);
            createNumericCell(stylesContainer, row, 9, producedQuantity.getDeviation(), false);
            createRegularCell(stylesContainer, row, 10, producedQuantity.getProductUnit());
            rowIndex++;
        }

        for (int i = 0; i < PRODUCED_QUANTITIES_HEADERS.size(); i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createMaterialCostsSheet(List<MaterialCost> materialCosts, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.orderNumber", locale),
                0);
        createHeaderCell(stylesContainer, row,
                translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.operationNumber", locale),
                1);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.productNumber", locale),
                2);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.productName", locale),
                3);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.replacementTo", locale),
                4);
        createHeaderCell(stylesContainer, row,
                translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.plannedQuantity", locale),
                5);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.usedQuantity", locale),
                6);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.materialCosts.quantitativeDeviation", locale),
                7);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.unit", locale),
                8);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.plannedCost", locale),
                9);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.realCost", locale),
                10);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.valueDeviation", locale),
                11);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.materialCosts.usedWasteQuantity", locale),
                12);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.materialCosts.unit", locale),
                13);

        int rowCounter = 0;
        for (MaterialCost materialCost : materialCosts) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, materialCost.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, materialCost.getOperationNumber());
            createRegularCell(stylesContainer, row, 2, materialCost.getProductNumber());
            createRegularCell(stylesContainer, row, 3, materialCost.getProductName());
            createRegularCell(stylesContainer, row, 4, materialCost.getReplacementTo());
            createNumericCell(stylesContainer, row, 5, materialCost.getPlannedQuantity(), false);
            createNumericCell(stylesContainer, row, 6, materialCost.getUsedQuantity(), true);
            createNumericCell(stylesContainer, row, 7, materialCost.getQuantitativeDeviation(), false);
            createRegularCell(stylesContainer, row, 8, materialCost.getProductUnit());
            createNumericCell(stylesContainer, row, 9, materialCost.getPlannedCost(), false);
            createNumericCell(stylesContainer, row, 10, materialCost.getRealCost(), true);
            createNumericCell(stylesContainer, row, 11, materialCost.getValueDeviation(), false);
            createNumericCell(stylesContainer, row, 12, materialCost.getUsedWasteQuantity(), false);
            createRegularCell(stylesContainer, row, 13, materialCost.getUsedWasteUnit());
            rowCounter++;
        }
        for (int i = 0; i <= 13; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createPieceworkSheet(HSSFSheet sheet, List<Long> ordersIds, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(stylesContainer, row, translationService.translate(PieceworkSheetConstants.ORDER_NUMBER, locale), 0);
        createHeaderCell(stylesContainer, row, translationService.translate(PieceworkSheetConstants.OPERATION_NUMBER, locale), 1);
        createHeaderCell(stylesContainer, row, translationService.translate("productionCounting.productionBalance.report.xls.sheet.piecework.worker", locale), 2);
        createHeaderCell(stylesContainer, row, translationService.translate("productionCounting.productionBalance.report.xls.sheet.piecework.producedQuantity", locale), 3);
        createHeaderCell(stylesContainer, row, translationService.translate("productionCounting.productionBalance.report.xls.sheet.piecework.pieceRate", locale), 4);
        createHeaderCell(stylesContainer, row, translationService.translate("productionCounting.productionBalance.report.xls.sheet.piecework.rate", locale), 5);
        createHeaderCell(stylesContainer, row, translationService.translate("productionCounting.productionBalance.report.xls.sheet.piecework.cost", locale), 6);

        List<PieceworkDetails> pieceworkDetailsList = productionBalanceRepository.getPieceworkDetails(ordersIds);
        int rowCounter = 0;
        for (PieceworkDetails pieceworkDetails : pieceworkDetailsList) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, pieceworkDetails.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, pieceworkDetails.getOperationNumber());
            createRegularCell(stylesContainer, row, 2, pieceworkDetails.getWorker());
            createNumericCell(stylesContainer, row, 3, pieceworkDetails.getProducedQuantity(), false);
            createRegularCell(stylesContainer, row, 4, pieceworkDetails.getPieceRate());
            createNumericCell(stylesContainer, row, 5, pieceworkDetails.getRate(), false);
            createNumericCell(stylesContainer, row, 6, pieceworkDetails.getCost(), false);
            rowCounter++;
        }
        for (int i = 0; i <= 6; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createStoppagesSheet(HSSFSheet sheet, List<Long> ordersIds, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.stoppages.orderNumber", locale),
                0);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.stoppages.productionTrackingNumber", locale),
                1);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.stoppages.productionTrackingState", locale),
                2);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages.duration", locale),
                3);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages.dateFrom", locale),
                4);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages.dateTo", locale), 5
        );
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages.reason", locale), 6
        );
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.stoppages.description", locale),
                7);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages.division", locale),
                8);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.stoppages.productionLine", locale),
                9);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.stoppages.workstation", locale),
                10);
        createHeaderCell(stylesContainer, row,
                translationService.translate("productionCounting.productionBalance.report.xls.sheet.stoppages.worker", locale),
                11);

        List<Stoppage> stoppages = productionBalanceRepository.getStoppages(ordersIds);
        int rowCounter = 0;
        for (Stoppage stoppage : stoppages) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, stoppage.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, stoppage.getProductionTrackingNumber());
            createRegularCell(stylesContainer, row, 2,
                    stoppage.getProductionTrackingState() != null ? translationService.translate(
                            "productionCounting.productionTracking.state.value." + stoppage.getProductionTrackingState(), locale)
                            : null);
            createTimeCell(stylesContainer, row, 3, stoppage.getDuration(), false);
            createDateTimeCell(stylesContainer, row, 4, stoppage.getDateFrom());
            createDateTimeCell(stylesContainer, row, 5, stoppage.getDateTo());
            createRegularCell(stylesContainer, row, 6, stoppage.getReason());
            createRegularCell(stylesContainer, row, 7, stoppage.getDescription());
            createRegularCell(stylesContainer, row, 8, stoppage.getDivision());
            createRegularCell(stylesContainer, row, 9, stoppage.getProductionLine());
            createRegularCell(stylesContainer, row, 10, stoppage.getWorkstation());
            createRegularCell(stylesContainer, row, 11, stoppage.getWorker());
            rowCounter++;
        }
        for (int i = 0; i <= 11; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createLaborTimeSheet(HSSFSheet sheet, List<Long> ordersIds, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.ORDER_NUMBER, locale), 0
        );
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.OPERATION_NUMBER, locale), 1
        );
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.STAFF_NUMBER, locale), 2
        );
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.STAFF_NAME, locale), 3
        );
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.STAFF_SURNAME, locale), 4
        );
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.WAGE_GROUP_NAME, locale), 5
        );
        createHeaderCell(stylesContainer, row,
                translationService.translate(LaborTimeSheetConstants.STAFF_LABOR_HOURLY_COST, locale), 6
        );
        createHeaderCell(stylesContainer, row, translationService.translate(LaborTimeSheetConstants.LABOR_TIME, locale), 7
        );

        List<LaborTime> laborTimeList = productionBalanceRepository.getLaborTime(ordersIds);
        int rowCounter = 0;
        for (LaborTime laborTime : laborTimeList) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, laborTime.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, laborTime.getOperationNumber());
            createRegularCell(stylesContainer, row, 2, laborTime.getStaffNumber());
            createRegularCell(stylesContainer, row, 3, laborTime.getStaffName());
            createRegularCell(stylesContainer, row, 4, laborTime.getStaffSurname());
            createRegularCell(stylesContainer, row, 5, laborTime.getWageGroupName());
            createNumericCell(stylesContainer, row, 6, laborTime.getStaffLaborHourlyCost(), false);
            createTimeCell(stylesContainer, row, 7, laborTime.getLaborTime(), false);
            rowCounter++;
        }
        for (int i = 0; i <= 7; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createLaborTimeDetailsSheet(List<LaborTimeDetails> laborTimeDetailsList, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.divisionNumber", locale),
                0);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.productionLineNumber", locale),
                1);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.orderNumber", locale),
                2);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.orderState", locale),
                3);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.plannedDateFrom", locale),
                4);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.effectiveDateFrom", locale),
                5);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.plannedDateTo", locale),
                6);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.effectiveDateTo", locale),
                7);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.productNumber", locale),
                8);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.orderName", locale),
                9);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.plannedQuantity", locale),
                10);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.amountOfProductProduced", locale),
                11);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.staffNumber", locale),
                12);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.staffName", locale),
                13);
        createHeaderCell(stylesContainer, row,
                translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.staffSurname", locale),
                14);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.operationNumber", locale),
                15);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.timeRangeFrom", locale),
                16);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.timeRangeTo", locale),
                17);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.shiftName", locale),
                18);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.createDate", locale),
                19);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.laborTime", locale),
                20);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.plannedLaborTime", locale),
                21);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.laborTimeDeviation", locale),
                22);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.machineTime", locale),
                23);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.plannedMachineTime", locale),
                24);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.laborTimeDetails.machineTimeDeviation", locale),
                25);

        int rowCounter = 0;
        for (LaborTimeDetails laborTimeDetails : laborTimeDetailsList) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, laborTimeDetails.getDivisionNumber());
            createRegularCell(stylesContainer, row, 1, laborTimeDetails.getProductionLineNumber());
            createRegularCell(stylesContainer, row, 2, laborTimeDetails.getOrderNumber());
            createRegularCell(stylesContainer, row, 3,
                    translationService.translate("orders.order.state.value." + laborTimeDetails.getOrderState(), locale));
            createDateTimeCell(stylesContainer, row, 4, laborTimeDetails.getPlannedDateFrom());
            createDateTimeCell(stylesContainer, row, 5, laborTimeDetails.getEffectiveDateFrom());
            createDateTimeCell(stylesContainer, row, 6, laborTimeDetails.getPlannedDateTo());
            createDateTimeCell(stylesContainer, row, 7, laborTimeDetails.getEffectiveDateTo());
            createRegularCell(stylesContainer, row, 8, laborTimeDetails.getProductNumber());
            createRegularCell(stylesContainer, row, 9, laborTimeDetails.getOrderName());
            createNumericCell(stylesContainer, row, 10, laborTimeDetails.getPlannedQuantity(), false);
            createNumericCell(stylesContainer, row, 11, laborTimeDetails.getAmountOfProductProduced(), false);
            createRegularCell(stylesContainer, row, 12, laborTimeDetails.getStaffNumber());
            createRegularCell(stylesContainer, row, 13, laborTimeDetails.getStaffName());
            createRegularCell(stylesContainer, row, 14, laborTimeDetails.getStaffSurname());
            createRegularCell(stylesContainer, row, 15, laborTimeDetails.getOperationNumber());
            createDateTimeCell(stylesContainer, row, 16, laborTimeDetails.getTimeRangeFrom());
            createDateTimeCell(stylesContainer, row, 17, laborTimeDetails.getTimeRangeTo());
            createRegularCell(stylesContainer, row, 18, laborTimeDetails.getShiftName());
            createDateTimeCell(stylesContainer, row, 19, laborTimeDetails.getCreateDate());
            createTimeCell(stylesContainer, row, 20, laborTimeDetails.getLaborTime(), false);
            createTimeCell(stylesContainer, row, 21, laborTimeDetails.getPlannedLaborTime(), false);
            createTimeCell(stylesContainer, row, 22, laborTimeDetails.getLaborTimeDeviation(), false);
            createTimeCell(stylesContainer, row, 23, laborTimeDetails.getMachineTime(), false);
            createTimeCell(stylesContainer, row, 24, laborTimeDetails.getPlannedMachineTime(), false);
            createTimeCell(stylesContainer, row, 25, laborTimeDetails.getMachineTimeDeviation(), false);
            rowCounter++;
        }
        for (int i = 0; i <= 25; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createProductionCostsSheet(List<ProductionCost> productionCosts, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.productionCosts.orderNumber", locale),
                0);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.operationNumber", locale),
                1);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.plannedCostsSum", locale),
                2);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.productionCosts.realCostsSum", locale),
                3);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.sumCostsDeviation", locale),
                4);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.plannedStaffTime", locale),
                5);
        createHeaderCell(stylesContainer, row,
                translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.productionCosts.realStaffTime", locale),
                6);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.plannedMachineTime", locale),
                7);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.realMachineTime", locale),
                8);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.plannedStaffCosts", locale),
                9);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.realStaffCosts", locale),
                10);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.staffCostsDeviation", locale),
                11);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.plannedMachineCosts", locale),
                12);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.realMachineCosts", locale),
                13);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.machineCostsDeviation", locale),
                14);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.pieceworkProduction", locale),
                15);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.plannedPieceworkCosts", locale),
                16);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.realPieceworkCosts", locale),
                17);

        int rowCounter = 0;
        for (ProductionCost productionCost : productionCosts) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, productionCost.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, productionCost.getOperationNumber());
            if (productionCost.isPieceworkProduction()) {
                createRegularCell(stylesContainer, row, 2, null);
                createRegularCell(stylesContainer, row, 3, null);
                createRegularCell(stylesContainer, row, 4, null);
            } else {
                createNumericCell(stylesContainer, row, 2, productionCost.getPlannedCostsSum(), false);
                createNumericCell(stylesContainer, row, 3, productionCost.getRealCostsSum(), false);
                createNumericCell(stylesContainer, row, 4, productionCost.getSumCostsDeviation(), false);
            }
            createTimeCell(stylesContainer, row, 5, productionCost.getPlannedStaffTime(), false);
            createTimeCell(stylesContainer, row, 6, productionCost.getRealStaffTime(), true);
            createTimeCell(stylesContainer, row, 7, productionCost.getPlannedMachineTime(), false);
            createTimeCell(stylesContainer, row, 8, productionCost.getRealMachineTime(), true);
            if (productionCost.isPieceworkProduction()) {
                createRegularCell(stylesContainer, row, 9, null);
                createRegularCell(stylesContainer, row, 10, null);
                createRegularCell(stylesContainer, row, 11, null);
                createRegularCell(stylesContainer, row, 12, null);
                createRegularCell(stylesContainer, row, 13, null);
                createRegularCell(stylesContainer, row, 14, null);
                createRegularCell(stylesContainer, row, 15, translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.pieceworkProduction.yes", locale));
                createNumericCell(stylesContainer, row, 16, productionCost.getPlannedPieceworkCosts(), false);
                createNumericCell(stylesContainer, row, 17, productionCost.getRealPieceworkCosts(), false);
            } else {
                createNumericCell(stylesContainer, row, 9, productionCost.getPlannedStaffCosts(), false);
                createNumericCell(stylesContainer, row, 10, productionCost.getRealStaffCosts(), false);
                createNumericCell(stylesContainer, row, 11, productionCost.getStaffCostsDeviation(), false);
                createNumericCell(stylesContainer, row, 12, productionCost.getPlannedMachineCosts(), false);
                createNumericCell(stylesContainer, row, 13, productionCost.getRealMachineCosts(), false);
                createNumericCell(stylesContainer, row, 14, productionCost.getMachineCostsDeviation(), false);
                createRegularCell(stylesContainer, row, 15, translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.productionCosts.pieceworkProduction.no", locale));
                createRegularCell(stylesContainer, row, 16, null);
                createRegularCell(stylesContainer, row, 17, null);
            }

            rowCounter++;
        }
        for (int i = 0; i <= 17; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createAdditionalCostsSheet(List<AdditionalCost> additionalCosts, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.additionalCosts.orderNumber", locale),
                0);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.additionalCosts.number", locale),
                1);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.additionalCosts.name", locale),
                2);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.additionalCosts.actualCost", locale),
                3);


        int rowCounter = 0;
        for (AdditionalCost additionalCost : additionalCosts) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, additionalCost.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, additionalCost.getNumber());
            createRegularCell(stylesContainer, row, 2, additionalCost.getName());
            createNumericCell(stylesContainer, row, 3, additionalCost.getActualCost(), false);
            rowCounter++;
        }
        for (int i = 0; i <= 3; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createOrdersBalanceSheet(List<OrderBalance> ordersBalance, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.orderNumber", locale),
                0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.productNumber", locale),
                1);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.productName", locale),
                2);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.additionalFinalProducts", locale),
                3);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.producedQuantity", locale),
                4);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.materialCosts", locale),
                5);
        createHeaderCell(stylesContainer, row,
                translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.productionCosts", locale),
                6);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.technicalProductionCosts", locale),
                7);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.materialCostMargin", locale),
                8);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.materialCostMarginValue", locale),
                9);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.productionCostMargin", locale),
                10);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.productionCostMarginValue", locale),
                11);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.additionalOverhead", locale),
                12);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.directAdditionalCost", locale),
                13);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.totalCosts", locale),
                14);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.registrationPrice", locale),
                15);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.registrationPriceOverhead", locale),
                16);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.registrationPriceOverheadValue",
                        locale),
                17);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.realProductionCosts", locale),
                18);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.technicalProductionCostOverhead",
                        locale),
                19);
        createHeaderCell(stylesContainer, row, translationService.translate(
                "productionCounting.productionBalance.report.xls.sheet.ordersBalance.technicalProductionCostOverheadValue",
                locale), 20);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.totalManufacturingCost", locale),
                21);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.profit", locale),
                22);

        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.profitValue", locale),
                23);

        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.sellPrice", locale),
                24);

        int rowCounter = 0;
        for (OrderBalance orderBalance : ordersBalance) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, orderBalance.getOrderNumber());
            createRegularCell(stylesContainer, row, 1, orderBalance.getProductNumber());
            createRegularCell(stylesContainer, row, 2, orderBalance.getProductName());
            createRegularCell(stylesContainer, row, 3, toYesOrNoFromString(orderBalance.getAdditionalFinalProducts(), locale));
            createNumericCell(stylesContainer, row, 4, orderBalance.getProducedQuantity(), true);
            createNumericCell(stylesContainer, row, 5, orderBalance.getMaterialCosts(), false);
            createNumericCell(stylesContainer, row, 6, orderBalance.getProductionCosts(), false);
            createNumericCell(stylesContainer, row, 7, orderBalance.getTechnicalProductionCosts(), true);
            createNumericCell(stylesContainer, row, 8, orderBalance.getMaterialCostMargin(), false);
            createNumericCell(stylesContainer, row, 9, orderBalance.getMaterialCostMarginValue(), false);
            createNumericCell(stylesContainer, row, 10, orderBalance.getProductionCostMargin(), false);
            createNumericCell(stylesContainer, row, 11, orderBalance.getProductionCostMarginValue(), false);
            createNumericCell(stylesContainer, row, 12, orderBalance.getAdditionalOverhead(), false);
            createNumericCell(stylesContainer, row, 13, orderBalance.getDirectAdditionalCost(), false);
            createNumericCell(stylesContainer, row, 14, orderBalance.getTotalCosts(), true);
            createNumericCell(stylesContainer, row, 15, orderBalance.getRegistrationPrice(), false);
            createNumericCell(stylesContainer, row, 16, orderBalance.getRegistrationPriceOverhead(), false);
            createNumericCell(stylesContainer, row, 17, orderBalance.getRegistrationPriceOverheadValue(), false);
            createNumericCell(stylesContainer, row, 18, orderBalance.getRealProductionCosts(), false);
            createNumericCell(stylesContainer, row, 19, orderBalance.getTechnicalProductionCostOverhead(), false);
            createNumericCell(stylesContainer, row, 20, orderBalance.getTechnicalProductionCostOverheadValue(), false);
            createNumericCell(stylesContainer, row, 21, orderBalance.getTotalManufacturingCost(), false);
            createNumericCell(stylesContainer, row, 22, orderBalance.getProfit(), false);
            createNumericCell(stylesContainer, row, 23, orderBalance.getProfitValue(), false);
            createNumericCell(stylesContainer, row, 24, orderBalance.getSellPrice(), false);
            rowCounter++;
        }
        for (int i = 0; i <= 24; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private String toYesOrNoFromString(String additionalFinalProducts, Locale locale) {
        return !Strings.isNullOrEmpty(additionalFinalProducts) ? translationService.translate("qcadooView.true", locale)
                : translationService.translate("qcadooView.false", locale);
    }

    private void createProductsBalanceSheet(List<OrderBalance> productsBalance, HSSFSheet sheet, Locale locale) {
        final FontsContainer fontsContainer = new FontsContainer(sheet.getWorkbook());
        final StylesContainer stylesContainer = new StylesContainer(sheet.getWorkbook(), fontsContainer);
        final int rowOffset = 1;
        HSSFRow row = sheet.createRow(0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.productNumber", locale),
                0);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.productName", locale),
                1);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.producedQuantity", locale),
                2);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.materialCosts", locale),
                3);
        createHeaderCell(stylesContainer, row,
                translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.productionCosts", locale),
                4);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.technicalProductionCosts", locale),
                5);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.materialCostMargin", locale),
                6);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.materialCostMarginValue", locale),
                7);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.productionCostMargin", locale),
                8);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.productionCostMarginValue", locale),
                9);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.additionalOverhead", locale),
                10);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.directAdditionalCost", locale),
                11);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.totalCosts", locale),
                12);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.registrationPrice", locale),
                13);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.registrationPriceOverhead", locale),
                14);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.registrationPriceOverheadValue",
                        locale),
                15);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.realProductionCosts", locale),
                16);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.technicalProductionCostOverhead",
                        locale),
                17);
        createHeaderCell(stylesContainer, row, translationService.translate(
                "productionCounting.productionBalance.report.xls.sheet.ordersBalance.technicalProductionCostOverheadValue",
                locale), 18);
        createHeaderCell(stylesContainer, row,
                translationService.translate(
                        "productionCounting.productionBalance.report.xls.sheet.ordersBalance.totalManufacturingCost", locale),
                19);
        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.profit", locale),
                20);

        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.profitValue", locale),
                21);

        createHeaderCell(
                stylesContainer, row, translationService
                        .translate("productionCounting.productionBalance.report.xls.sheet.ordersBalance.sellPrice", locale),
                22);

        int rowCounter = 0;
        for (OrderBalance orderBalance : productsBalance) {
            row = sheet.createRow(rowOffset + rowCounter);
            createRegularCell(stylesContainer, row, 0, orderBalance.getProductNumber());
            createRegularCell(stylesContainer, row, 1, orderBalance.getProductName());
            createNumericCell(stylesContainer, row, 2, orderBalance.getProducedQuantity(), true);
            createNumericCell(stylesContainer, row, 3, orderBalance.getMaterialCosts(), false);
            createNumericCell(stylesContainer, row, 4, orderBalance.getProductionCosts(), false);
            createNumericCell(stylesContainer, row, 5, orderBalance.getTechnicalProductionCosts(), true);
            createNumericCell(stylesContainer, row, 6, orderBalance.getMaterialCostMargin(), false);
            createNumericCell(stylesContainer, row, 7, orderBalance.getMaterialCostMarginValue(), false);
            createNumericCell(stylesContainer, row, 8, orderBalance.getProductionCostMargin(), false);
            createNumericCell(stylesContainer, row, 9, orderBalance.getProductionCostMarginValue(), false);
            createNumericCell(stylesContainer, row, 10, orderBalance.getAdditionalOverhead(), false);
            createNumericCell(stylesContainer, row, 11, orderBalance.getDirectAdditionalCost(), false);
            createNumericCell(stylesContainer, row, 12, orderBalance.getTotalCosts(), true);
            createNumericCell(stylesContainer, row, 13, orderBalance.getRegistrationPrice(), false);
            createNumericCell(stylesContainer, row, 14, orderBalance.getRegistrationPriceOverhead(), false);
            createNumericCell(stylesContainer, row, 15, orderBalance.getRegistrationPriceOverheadValue(), false);
            createNumericCell(stylesContainer, row, 16, orderBalance.getRealProductionCosts(), false);
            createNumericCell(stylesContainer, row, 17, orderBalance.getTechnicalProductionCostOverhead(), false);
            createNumericCell(stylesContainer, row, 18, orderBalance.getTechnicalProductionCostOverheadValue(), false);
            createNumericCell(stylesContainer, row, 19, orderBalance.getTotalManufacturingCost(), false);
            createNumericCell(stylesContainer, row, 20, orderBalance.getProfit(), false);
            createNumericCell(stylesContainer, row, 21, orderBalance.getProfitValue(), false);
            createNumericCell(stylesContainer, row, 22, orderBalance.getSellPrice(), false);
            rowCounter++;
        }
        for (int i = 0; i <= 22; i++) {
            sheet.autoSizeColumn(i, false);
        }
    }

    private void createRegularCell(StylesContainer stylesContainer, HSSFRow row, int column, String content) {
        HSSFCell cell = row.createCell(column);
        cell.setCellValue(content);
        cell.setCellStyle(StylesContainer.aligned(stylesContainer.regularStyle, HorizontalAlignment.LEFT));
    }

    private void createNumericCell(StylesContainer stylesContainer, HSSFRow row, int column, BigDecimal value, boolean bold) {
        HSSFCell cell = row.createCell(column, HSSFCell.CELL_TYPE_NUMERIC);
        cell.setCellValue(numberService.setScaleWithDefaultMathContext(value, 2).doubleValue());
        if (bold) {
            cell.setCellStyle(StylesContainer.aligned(stylesContainer.numberBoldStyle, HorizontalAlignment.RIGHT));
        } else {
            cell.setCellStyle(StylesContainer.aligned(stylesContainer.numberStyle, HorizontalAlignment.RIGHT));
        }
    }

    private void createTimeCell(StylesContainer stylesContainer, HSSFRow row, int column, Integer value, boolean bold) {
        HSSFCell cell = row.createCell(column, HSSFCell.CELL_TYPE_NUMERIC);
        if (value == null) {
            value = 0;
        }
        cell.setCellValue(Math.abs(value) / 86400d);
        if (value >= 0) {
            if (bold) {
                cell.setCellStyle(StylesContainer.aligned(stylesContainer.timeBoldStyle, HorizontalAlignment.RIGHT));
            } else {
                cell.setCellStyle(StylesContainer.aligned(stylesContainer.timeStyle, HorizontalAlignment.RIGHT));
            }
        } else {
            cell.setCellStyle(StylesContainer.aligned(stylesContainer.negativeTimeStyle, HorizontalAlignment.RIGHT));
        }
    }

    private void createDateTimeCell(StylesContainer stylesContainer, HSSFRow row, int column, Date value) {
        HSSFCell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(StylesContainer.aligned(stylesContainer.dateTimeStyle, HorizontalAlignment.RIGHT));
        }
    }

    private void createHeaderCell(StylesContainer stylesContainer, HSSFRow row, String content, int column) {
        HSSFCell cell = row.createCell(column);
        cell.setCellValue(content);
        cell.setCellStyle(StylesContainer.aligned(stylesContainer.headerStyle, HorizontalAlignment.LEFT));
    }

    private static class StylesContainer {

        private final HSSFCellStyle regularStyle;

        private final HSSFCellStyle headerStyle;

        private final HSSFCellStyle timeStyle;

        private final HSSFCellStyle timeBoldStyle;

        private final HSSFCellStyle negativeTimeStyle;

        private final HSSFCellStyle numberStyle;

        private final HSSFCellStyle numberBoldStyle;

        private final HSSFCellStyle dateTimeStyle;

        StylesContainer(HSSFWorkbook workbook, FontsContainer fontsContainer) {
            regularStyle = workbook.createCellStyle();
            regularStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            headerStyle = workbook.createCellStyle();
            headerStyle.setFont(fontsContainer.boldFont);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setWrapText(true);

            timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(workbook.createDataFormat().getFormat("[HH]:MM:SS"));

            timeBoldStyle = workbook.createCellStyle();
            timeBoldStyle.setDataFormat(workbook.createDataFormat().getFormat("[HH]:MM:SS"));
            timeBoldStyle.setFont(fontsContainer.boldFont);

            negativeTimeStyle = workbook.createCellStyle();
            negativeTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("-[HH]:MM:SS"));

            numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00###"));

            numberBoldStyle = workbook.createCellStyle();
            numberBoldStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00###"));
            numberBoldStyle.setFont(fontsContainer.boldFont);

            dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        }

        private static HSSFCellStyle aligned(HSSFCellStyle style, HorizontalAlignment horizontalAlignment) {
            style.setAlignment(horizontalAlignment);
            return style;
        }
    }

    private static class FontsContainer {

        private final Font boldFont;

        FontsContainer(HSSFWorkbook workbook) {
            boldFont = workbook.createFont();
            boldFont.setBold(true);
        }
    }
}
