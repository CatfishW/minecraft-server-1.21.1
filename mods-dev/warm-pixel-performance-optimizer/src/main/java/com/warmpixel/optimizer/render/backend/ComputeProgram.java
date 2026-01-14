package com.warmpixel.optimizer.render.backend;

import static org.lwjgl.opengl.GL46.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComputeProgram {
    private static final Logger LOGGER = LoggerFactory.getLogger("ComputeProgram");
    private final int programHandle;

    public ComputeProgram() {
        this.programHandle = glCreateProgram();
    }

    public void attachShader(ComputeShader shader) {
        glAttachShader(programHandle, shader.getShaderHandle());
    }

    public boolean link() {
        glLinkProgram(programHandle);
        boolean success = glGetProgrami(programHandle, GL_LINK_STATUS) == GL_TRUE;
        if (!success) {
             String log = glGetProgramInfoLog(programHandle);
             LOGGER.error("Failed to link compute program: {}", log);
        }
        return success;
    }

    public void use() {
        glUseProgram(programHandle);
    }
    
    public void unuse() {
        glUseProgram(0);
    }

    public void dispatch(int x, int y, int z) {
        glDispatchCompute(x, y, z);
    }

    public int getUniformLocation(String name) {
        return glGetUniformLocation(programHandle, name);
    }
    
    public void setUniform1ui(int location, int value) {
        glUniform1ui(location, value);
    }

    public void delete() {
        glDeleteProgram(programHandle);
    }
}
