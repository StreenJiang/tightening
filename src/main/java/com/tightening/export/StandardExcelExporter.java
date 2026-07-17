package com.tightening.export;

import com.tightening.entity.TighteningData;
import com.tightening.service.TighteningDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 标准 Excel 导出实现。将拧紧数据导出为 .xlsx 文件。
 */
@Slf4j
@Component
public class StandardExcelExporter implements Exporter {

    private static final String TYPE = "standard_excel";
    private static final String EXPORT_DIR = System.getProperty("user.home") + "/tightening_system/exports/";

    private final TighteningDataService tighteningDataService;

    public StandardExcelExporter(TighteningDataService tighteningDataService) {
        this.tighteningDataService = tighteningDataService;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ExportResult execute(ExportPayload payload) {
        try {
            Files.createDirectories(Path.of(EXPORT_DIR));

            List<TighteningData> dataList = tighteningDataService.listByMissionRecordId(payload.missionRecordId());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String filename = payload.missionRecordId() + "_" + timestamp + ".xlsx";
            Path filePath = Path.of(EXPORT_DIR, filename);

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("TighteningData");

                String[] columns = {
                        "id", "deleted", "creatorId", "modifierId", "createTime", "modifyTime",
                        "missionRecordId", "workstationName", "toolName", "toolTypeName", "productSideName",
                        "boltSerialNum", "armLocation", "parameterSet", "parameterSetName",
                        "tighteningId", "tighteningStatus", "resultType", "torqueStatus", "angleStatus",
                        "rundownAngleStatus", "torqueValuesUnit",
                        "torqueMinLimit", "torqueMaxLimit", "torqueFinalTarget", "torque",
                        "angleMinLimit", "angleMaxLimit", "angleFinalTarget", "angle",
                        "rundownAngleMinLimit", "rundownAngleMaxLimit", "rundownAngle",
                        "timestamp",
                        "cellId", "channelId", "controllerName", "vin", "jobId",
                        "batchSize", "batchCounter", "batchStatus",
                        "revision", "extraData"
                };

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columns.length; i++) {
                    headerRow.createCell(i).setCellValue(columns[i]);
                }

                int rowNum = 1;
                for (TighteningData data : dataList) {
                    Row row = sheet.createRow(rowNum++);
                    int c = 0;
                    setCell(row, c++, data.getId());
                    setCell(row, c++, data.getDeleted());
                    setCell(row, c++, data.getCreatorId());
                    setCell(row, c++, data.getModifierId());
                    setCell(row, c++, data.getCreateTime() != null ? data.getCreateTime().toString() : null);
                    setCell(row, c++, data.getModifyTime() != null ? data.getModifyTime().toString() : null);
                    setCell(row, c++, data.getMissionRecordId());
                    setCell(row, c++, data.getWorkstationName());
                    setCell(row, c++, data.getToolName());
                    setCell(row, c++, data.getToolTypeName());
                    setCell(row, c++, data.getProductSideName());
                    setCell(row, c++, data.getBoltSerialNum());
                    setCell(row, c++, data.getArmLocation());
                    setCell(row, c++, data.getParameterSet());
                    setCell(row, c++, data.getParameterSetName());
                    setCell(row, c++, data.getTighteningId());
                    setCell(row, c++, data.getTighteningStatus());
                    setCell(row, c++, data.getResultType());
                    setCell(row, c++, data.getTorqueStatus());
                    setCell(row, c++, data.getAngleStatus());
                    setCell(row, c++, data.getRundownAngleStatus());
                    setCell(row, c++, data.getTorqueValuesUnit());
                    setCell(row, c++, data.getTorqueMinLimit());
                    setCell(row, c++, data.getTorqueMaxLimit());
                    setCell(row, c++, data.getTorqueFinalTarget());
                    setCell(row, c++, data.getTorque());
                    setCell(row, c++, data.getAngleMinLimit());
                    setCell(row, c++, data.getAngleMaxLimit());
                    setCell(row, c++, data.getAngleFinalTarget());
                    setCell(row, c++, data.getAngle());
                    setCell(row, c++, data.getRundownAngleMinLimit());
                    setCell(row, c++, data.getRundownAngleMaxLimit());
                    setCell(row, c++, data.getRundownAngle());
                    setCell(row, c++, data.getTimestamp());
                    setCell(row, c++, data.getCellId());
                    setCell(row, c++, data.getChannelId());
                    setCell(row, c++, data.getControllerName());
                    setCell(row, c++, data.getVin());
                    setCell(row, c++, data.getJobId());
                    setCell(row, c++, data.getBatchSize());
                    setCell(row, c++, data.getBatchCounter());
                    setCell(row, c++, data.getBatchStatus());
                    setCell(row, c++, data.getRevision());
                    setCell(row, c++, data.getExtraData());
                }

                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                    workbook.write(fos);
                }
            }

            log.info("Exported Excel: {}", filename);
            return ExportResult.ok(filename);

        } catch (Exception e) {
            log.error("Excel export failed: {}", e.getMessage(), e);
            return ExportResult.fail(e.getMessage());
        }
    }

    private static void setCell(Row row, int idx, String value) {
        row.createCell(idx).setCellValue(value != null ? value : "");
    }

    private static void setCell(Row row, int idx, Long value) {
        row.createCell(idx).setCellValue(value != null ? value : 0L);
    }

    private static void setCell(Row row, int idx, Integer value) {
        row.createCell(idx).setCellValue(value != null ? value : 0);
    }

    private static void setCell(Row row, int idx, int value) {
        row.createCell(idx).setCellValue(value);
    }

    private static void setCell(Row row, int idx, long value) {
        row.createCell(idx).setCellValue(value);
    }

    private static void setCell(Row row, int idx, double value) {
        row.createCell(idx).setCellValue(value);
    }
}
