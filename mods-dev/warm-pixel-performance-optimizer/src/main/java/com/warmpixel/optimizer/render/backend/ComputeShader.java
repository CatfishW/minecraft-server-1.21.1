package com.warmpixel.optimizer.render.backend;

import static org.lwjgl.opengl.GL46.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeShader {
    private static final Logger LOGGER = LoggerFactory.getLogger("ComputeShader");
    private final int shaderHandle;

    public ComputeShader() {
        this.shaderHandle = glCreateShader(GL_COMPUTE_SHADER);
    }

    public void setShaderSource(String source) {
        glShaderSource(shaderHandle, source);
    }

    public boolean compileShader() {
        glCompileShader(shaderHandle);
        boolean success = glGetShaderi(shaderHandle, GL_COMPILE_STATUS) == GL_TRUE;
        if (!success) {
            String log = glGetShaderInfoLog(shaderHandle);
            LOGGER.error("Failed to compile compute shader: {}", log);
        }
        return success;
    }

    public int getShaderHandle() {
        return shaderHandle;
    }

    public void delete() {
        glDeleteShader(shaderHandle);
    }
}
