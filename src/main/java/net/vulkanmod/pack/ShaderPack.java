package net.vulkanmod.pack;

import net.vulkanmod.pipeline.PipelineType;

import java.nio.file.Path;
import java.util.*;

/**
 * Represents a loaded Vulkan shader pack.
 * Contains metadata and pipeline configurations.
 */
public class ShaderPack {
    
    private final String name;
    private final String version;
    private final String description;
    private final Path packPath;
    private final boolean raytracingSupported;
    private final List<PipelineDefinition> pipelines;
    
    public ShaderPack(ShaderPackConfig config, Path packPath) {
        this.name = config.name;
        this.version = config.version;
        this.description = config.description != null ? config.description : "";
        this.packPath = packPath;
        this.raytracingSupported = config.raytracing;
        this.pipelines = new ArrayList<>();
        
        // Parse pipeline configurations
        if (config.pipelines != null) {
            for (ShaderPackConfig.PipelineConfig pipelineConfig : config.pipelines) {
                pipelines.add(new PipelineDefinition(pipelineConfig, packPath));
            }
        }
    }
    
    public String getName() {
        return name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Path getPackPath() {
        return packPath;
    }
    
    public boolean isRaytracingSupported() {
        return raytracingSupported;
    }
    
    public List<PipelineDefinition> getPipelines() {
        return Collections.unmodifiableList(pipelines);
    }
    
    public PipelineDefinition getPipelineByName(String name) {
        return pipelines.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public String toString() {
        return "ShaderPack{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", pipelines=" + pipelines.size() +
                ", raytracing=" + raytracingSupported +
                '}';
    }
    
    /**
     * Represents a single pipeline definition from a shader pack.
     */
    public static class PipelineDefinition {
        private final String name;
        private final PipelineType type;
        private final String target;
        private final String vertexShader;
        private final String fragmentShader;
        private final String computeShader;
        private final String configPath;
        private final List<DescriptorSetDefinition> descriptorSets;
        private final List<String> requiredExtensions;
        
        public PipelineDefinition(ShaderPackConfig.PipelineConfig config, Path packPath) {
            this.name = config.name;
            this.type = PipelineType.fromString(config.type);
            this.target = config.target != null ? config.target : "";
            this.vertexShader = config.vertex != null ? config.vertex : "";
            this.fragmentShader = config.fragment != null ? config.fragment : "";
            this.computeShader = config.compute != null ? config.compute : "";
            this.configPath = config.config != null ? config.config : "";
            this.requiredExtensions = config.extensions != null ? new ArrayList<>(config.extensions) : new ArrayList<>();
            
            this.descriptorSets = new ArrayList<>();
            if (config.descriptor_sets != null) {
                for (ShaderPackConfig.DescriptorSetConfig dsConfig : config.descriptor_sets) {
                    descriptorSets.add(new DescriptorSetDefinition(dsConfig));
                }
            }
        }
        
        public String getName() {
            return name;
        }
        
        public PipelineType getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }
        
        public String getVertexShader() {
            return vertexShader;
        }
        
        public String getFragmentShader() {
            return fragmentShader;
        }
        
        public String getComputeShader() {
            return computeShader;
        }

        public String getConfigPath() {
            return configPath;
        }
        
        public List<DescriptorSetDefinition> getDescriptorSets() {
            return Collections.unmodifiableList(descriptorSets);
        }
        
        public List<String> getRequiredExtensions() {
            return Collections.unmodifiableList(requiredExtensions);
        }
    }
    
    /**
     * Represents a descriptor set definition from a shader pack.
     */
    public static class DescriptorSetDefinition {
        private final int setIndex;
        private final List<BindingDefinition> bindings;
        
        public DescriptorSetDefinition(ShaderPackConfig.DescriptorSetConfig config) {
            this.setIndex = config.set;
            this.bindings = new ArrayList<>();
            
            if (config.bindings != null) {
                for (ShaderPackConfig.BindingConfig bindingConfig : config.bindings) {
                    bindings.add(new BindingDefinition(bindingConfig));
                }
            }
        }
        
        public int getSetIndex() {
            return setIndex;
        }
        
        public List<BindingDefinition> getBindings() {
            return Collections.unmodifiableList(bindings);
        }
    }
    
    /**
     * Represents a binding definition from a shader pack.
     */
    public static class BindingDefinition {
        private final int bindingIndex;
        private final String type;
        private final String name;
        private final int arraySize;
        
        public BindingDefinition(ShaderPackConfig.BindingConfig config) {
            this.bindingIndex = config.binding;
            this.type = config.type;
            this.name = config.name;
            this.arraySize = config.array_size;
        }
        
        public int getBindingIndex() {
            return bindingIndex;
        }
        
        public String getType() {
            return type;
        }
        
        public String getName() {
            return name;
        }
        
        public int getArraySize() {
            return arraySize;
        }
    }
}
