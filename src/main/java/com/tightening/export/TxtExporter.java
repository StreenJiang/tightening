package com.tightening.export;

import com.tightening.entity.TighteningData;
import com.tightening.service.TighteningDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TXT (CSV) 导出实现。将拧紧数据导出为逗号分隔、双引号转义的文本文件。
 */
@Slf4j
@Component
public class TxtExporter implements Exporter {

    private static final String TYPE = "txt";
    private static final String EXPORT_DIR = System.getProperty("user.home") + "/tightening_system/exports/";

    private final TighteningDataService tighteningDataService;

    public TxtExporter(TighteningDataService tighteningDataService) {
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

            List<TighteningData> dataList = tighteningDataService.listByTaskRecordId(payload.taskRecordId());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String filename = payload.taskRecordId() + "_" + timestamp + ".txt";
            Path filePath = Path.of(EXPORT_DIR, filename);

            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                String[] header = {
                        "id", "deleted", "creatorId", "modifierId", "createTime", "modifyTime",
                        "taskRecordId", "workstationName", "toolName", "toolTypeName", "productSideName",
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
                writer.write(String.join(",", header));
                writer.newLine();

                for (TighteningData data : dataList) {
                    writer.write(toCsvRow(data));
                    writer.newLine();
                }
            }

            log.info("Exported TXT: {}", filename);
            return ExportResult.ok(filename);

        } catch (Exception e) {
            log.error("TXT export failed: {}", e.getMessage(), e);
            return ExportResult.fail(e.getMessage());
        }
    }

    private static String toCsvRow(TighteningData data) {
        return String.join(",", List.of(
                csv(data.getId()),
                csv(data.getDeleted()),
                csv(data.getCreatorId()),
                csv(data.getModifierId()),
                csv(data.getCreateTime()),
                csv(data.getModifyTime()),
                csv(data.getTaskRecordId()),
                csv(data.getWorkstationName()),
                csv(data.getToolName()),
                csv(data.getToolTypeName()),
                csv(data.getProductSideName()),
                csv(data.getBoltSerialNum()),
                csv(data.getArmLocation()),
                csv(data.getParameterSet()),
                csv(data.getParameterSetName()),
                csv(data.getTighteningId()),
                csv(data.getTighteningStatus()),
                csv(data.getResultType()),
                csv(data.getTorqueStatus()),
                csv(data.getAngleStatus()),
                csv(data.getRundownAngleStatus()),
                csv(data.getTorqueValuesUnit()),
                csv(data.getTorqueMinLimit()),
                csv(data.getTorqueMaxLimit()),
                csv(data.getTorqueFinalTarget()),
                csv(data.getTorque()),
                csv(data.getAngleMinLimit()),
                csv(data.getAngleMaxLimit()),
                csv(data.getAngleFinalTarget()),
                csv(data.getAngle()),
                csv(data.getRundownAngleMinLimit()),
                csv(data.getRundownAngleMaxLimit()),
                csv(data.getRundownAngle()),
                csv(data.getTimestamp()),
                csv(data.getCellId()),
                csv(data.getChannelId()),
                csv(data.getControllerName()),
                csv(data.getVin()),
                csv(data.getJobId()),
                csv(data.getBatchSize()),
                csv(data.getBatchCounter()),
                csv(data.getBatchStatus()),
                csv(data.getRevision()),
                csv(data.getExtraData())
        ));
    }

    private static String csv(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String csv(Long value) {
        return value != null ? value.toString() : "";
    }

    private static String csv(Integer value) {
        return value != null ? value.toString() : "";
    }

    private static String csv(int value) {
        return Integer.toString(value);
    }

    private static String csv(long value) {
        return Long.toString(value);
    }

    private static String csv(double value) {
        return Double.toString(value);
    }

    private static String csv(Object value) {
        if (value == null) return "";
        return csv(value.toString());
    }
}
