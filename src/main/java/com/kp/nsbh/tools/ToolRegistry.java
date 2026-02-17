package com.kp.nsbh.tools;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    private final Map<String, Tool> toolByName;
    private final Map<String, ToolMetadata> metadataByName;

    public ToolRegistry(List<Tool> tools) {
        Map<String, Tool> toolMap = new LinkedHashMap<>();
        Map<String, ToolMetadata> metadataMap = new LinkedHashMap<>();

        for (Tool tool : tools) {
            Class<?> toolClass = AopUtils.getTargetClass(tool);
            NsbhTool annotation = AnnotationUtils.findAnnotation(toolClass, NsbhTool.class);
            if (annotation == null) {
                throw new IllegalStateException("Tool bean is missing @NsbhTool: " + toolClass.getName());
            }

            String name = annotation.name();
            if (toolMap.containsKey(name)) {
                throw new IllegalStateException("Duplicate tool name: " + name);
            }

            toolMap.put(name, tool);
            metadataMap.put(name, new ToolMetadata(
                    name,
                    annotation.description(),
                    annotation.schema(),
                    Arrays.asList(annotation.requiredPermissions())
            ));
        }

        this.toolByName = Map.copyOf(toolMap);
        this.metadataByName = Map.copyOf(metadataMap);
    }

    public Tool findTool(String name) {
        return toolByName.get(name);
    }

    public ToolMetadata findMetadata(String name) {
        return metadataByName.get(name);
    }

    public List<ToolMetadata> listMetadata() {
        return metadataByName.values().stream()
                .sorted(Comparator.comparing(ToolMetadata::name))
                .toList();
    }
}
