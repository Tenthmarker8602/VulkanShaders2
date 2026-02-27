package net.vulkanmod.render.shader.bsl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves #include directives in BSL/OptiFine-format GLSL shaders.
 * Handles recursive includes, circular include prevention, and
 * both absolute paths ("/lib/...") and relative paths ("file.glsl").
 */
public class BSLIncludeResolver {

    // Match both absolute (#include "/path/file.glsl") and relative (#include "file.glsl") includes
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "^\\s*#include\\s+\"([^\"]+)\"", Pattern.MULTILINE);

    /**
     * Resolve all #include directives recursively.
     *
     * @param shaderRoot The BSL pack's shaders/ directory
     * @param source     The initial shader source (e.g., the world0 wrapper file)
     * @return Fully merged source with all includes inlined
     */
    public static String resolve(Path shaderRoot, String source) {
        Set<String> included = new HashSet<>();
        // Initial file is in the shaderRoot itself (e.g., world0/gbuffers_terrain.fsh)
        return resolveRecursive(shaderRoot, shaderRoot, source, included);
    }

    /**
     * @param shaderRoot  The BSL pack's shaders/ directory (for absolute paths)
     * @param currentDir  The directory of the file currently being processed (for relative paths)
     * @param source      The source to process
     * @param included    Set of already-included canonical paths to prevent circular includes
     */
    private static String resolveRecursive(Path shaderRoot, Path currentDir, String source, Set<String> included) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = INCLUDE_PATTERN.matcher(source);
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before this #include
            result.append(source, lastEnd, matcher.start());

            String includePath = matcher.group(1); // e.g., "/program/gbuffers_terrain.glsl" or "lightColor.glsl"
            Path resolvedPath;

            if (includePath.startsWith("/")) {
                // Absolute path: relative to shaderRoot
                resolvedPath = shaderRoot.resolve(includePath.substring(1));
            } else {
                // Relative path: relative to the current file's directory
                resolvedPath = currentDir.resolve(includePath);
            }

            // Normalize for deduplication
            String canonicalKey;
            try {
                canonicalKey = resolvedPath.toRealPath().toString();
            } catch (IOException e) {
                canonicalKey = resolvedPath.normalize().toAbsolutePath().toString();
            }

            if (included.contains(canonicalKey)) {
                // Already included — skip to prevent circular includes
                result.append("// [BSL] Already included: ").append(includePath).append("\n");
            } else {
                included.add(canonicalKey);

                if (Files.exists(resolvedPath)) {
                    try {
                        String includeSource = Files.readString(resolvedPath);
                        // The included file's directory becomes currentDir for nested includes
                        Path includeDir = resolvedPath.getParent();
                        // Recursively resolve includes within the included file
                        String resolved = resolveRecursive(shaderRoot, includeDir, includeSource, included);
                        result.append("// [BSL] Begin include: ").append(includePath).append("\n");
                        result.append(resolved);
                        result.append("\n// [BSL] End include: ").append(includePath).append("\n");
                    } catch (IOException e) {
                        System.err.println("[BSL] Failed to read include: " + includePath + " -> " + e.getMessage());
                        result.append("// [BSL] ERROR: Failed to read include: ").append(includePath).append("\n");
                    }
                } else {
                    System.err.println("[BSL] Include file not found: " + resolvedPath);
                    result.append("// [BSL] ERROR: File not found: ").append(includePath).append("\n");
                }
            }

            lastEnd = matcher.end();
        }

        // Append remaining text after last #include
        result.append(source, lastEnd, source.length());

        return result.toString();
    }
}
