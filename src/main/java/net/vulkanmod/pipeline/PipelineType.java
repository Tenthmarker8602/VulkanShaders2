package net.vulkanmod.pipeline;

/**
 * Enumeration for supported pipeline types.
 */
public enum PipelineType {
    GRAPHICS("graphics"),
    COMPUTE("compute"),
    RAY_TRACING("ray_tracing");
    
    private final String name;
    
    PipelineType(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public static PipelineType fromString(String name) {
        for (PipelineType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown pipeline type: " + name);
    }
}
