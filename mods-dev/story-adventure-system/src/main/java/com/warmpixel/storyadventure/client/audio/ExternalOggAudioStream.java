package com.warmpixel.storyadventure.client.audio;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An AudioStream that decodes an external OGG file using STBVorbis.
 * Implements Minecraft's AudioStream interface for proper integration.
 */
@Environment(EnvType.CLIENT)
public class ExternalOggAudioStream implements AudioStream {
    
    private ByteBuffer pcmData;
    private final AudioFormat format;
    private final int totalBytes;
    private int position = 0;
    private boolean closed = false;

    public ExternalOggAudioStream(Path filePath) throws IOException {
        StoryAdventureMod.LOGGER.debug("[ExternalOggAudioStream] Loading: {}", filePath);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Audio file not found: " + filePath);
        }
        
        byte[] fileData = Files.readAllBytes(filePath);
        if (fileData.length == 0) {
            throw new IOException("Audio file is empty: " + filePath);
        }
        
        ByteBuffer fileBuffer = null;
        ShortBuffer shortBuffer = null;
        
        try {
            fileBuffer = MemoryUtil.memAlloc(fileData.length);
            fileBuffer.put(fileData).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer errorBuffer = stack.mallocInt(1);
                long decoder = STBVorbis.stb_vorbis_open_memory(fileBuffer, errorBuffer, null);
                
                if (decoder == 0) {
                    int error = errorBuffer.get(0);
                    throw new IOException("Failed to open OGG file: " + filePath + ", error code: " + error + " (" + getVorbisError(error) + ")");
                }

                try {
                    STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                    STBVorbis.stb_vorbis_get_info(decoder, info);
                    
                    int channels = info.channels();
                    int sampleRate = info.sample_rate();
                    int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
                    
                    if (totalSamples <= 0) {
                        throw new IOException("Invalid OGG file (0 samples): " + filePath);
                    }
                    
                    if (channels <= 0 || channels > 2) {
                        throw new IOException("Unsupported channel count: " + channels + " in " + filePath);
                    }
                    
                    if (sampleRate <= 0) {
                        throw new IOException("Invalid sample rate: " + sampleRate + " in " + filePath);
                    }

                    // Allocate buffer for decoded samples
                    int totalShorts = totalSamples * channels;
                    shortBuffer = MemoryUtil.memAllocShort(totalShorts);
                    
                    // Decode all samples
                    int samplesDecoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                        decoder, channels, shortBuffer
                    );
                    
                    if (samplesDecoded <= 0) {
                        throw new IOException("Failed to decode OGG samples from: " + filePath);
                    }
                    
                    int actualShorts = samplesDecoded * channels;
                    this.totalBytes = actualShorts * 2; // 2 bytes per short (16-bit audio)
                    
                    // Allocate PCM buffer and copy data
                    this.pcmData = MemoryUtil.memAlloc(this.totalBytes);
                    
                    // Convert shorts to bytes in little-endian format
                    for (int i = 0; i < actualShorts; i++) {
                        short sample = shortBuffer.get(i);
                        pcmData.put((byte) (sample & 0xFF));
                        pcmData.put((byte) ((sample >> 8) & 0xFF));
                    }
                    pcmData.flip();

                    // Create audio format: 16-bit, signed, little-endian
                    this.format = new AudioFormat(
                        (float) sampleRate,
                        16,
                        channels,
                        true,   // signed
                        false   // little-endian
                    );
                    
                    StoryAdventureMod.LOGGER.info(
                        "[ExternalOggAudioStream] Loaded: {} ({}Hz, {} ch, {} samples, {} bytes)", 
                        filePath.getFileName(), sampleRate, channels, samplesDecoded, totalBytes
                    );
                    
                } finally {
                    STBVorbis.stb_vorbis_close(decoder);
                }
            }
        } catch (IOException e) {
            // Clean up on error
            if (pcmData != null) {
                MemoryUtil.memFree(pcmData);
                pcmData = null;
            }
            throw e;
        } catch (Exception e) {
            if (pcmData != null) {
                MemoryUtil.memFree(pcmData);
                pcmData = null;
            }
            throw new IOException("Error loading OGG file: " + filePath, e);
        } finally {
            // Always free temporary buffers
            if (shortBuffer != null) {
                MemoryUtil.memFree(shortBuffer);
            }
            if (fileBuffer != null) {
                MemoryUtil.memFree(fileBuffer);
            }
        }
    }
    
    private static String getVorbisError(int error) {
        return switch (error) {
            case 1 -> "VORBIS_need_more_data";
            case 2 -> "VORBIS_invalid_api_mixing";
            case 3 -> "VORBIS_outofmem";
            case 4 -> "VORBIS_feature_not_supported";
            case 5 -> "VORBIS_too_many_channels";
            case 6 -> "VORBIS_file_open_failure";
            case 7 -> "VORBIS_seek_without_length";
            case 10 -> "VORBIS_unexpected_eof";
            case 20 -> "VORBIS_seek_invalid";
            case 21 -> "VORBIS_invalid_setup";
            case 30 -> "VORBIS_invalid_stream";
            case 31 -> "VORBIS_missing_capture_pattern";
            case 32 -> "VORBIS_invalid_stream_structure_version";
            case 33 -> "VORBIS_continued_packet_flag_invalid";
            case 34 -> "VORBIS_incorrect_stream_serial_number";
            case 35 -> "VORBIS_invalid_first_page";
            case 36 -> "VORBIS_bad_packet_type";
            case 37 -> "VORBIS_cant_find_last_page";
            case 38 -> "VORBIS_seek_failed";
            default -> "Unknown error";
        };
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (closed || pcmData == null) {
            return MemoryUtil.memAlloc(0);
        }
        
        int remaining = totalBytes - position;
        int toRead = Math.min(size, remaining);
        
        if (toRead <= 0) {
            return MemoryUtil.memAlloc(0);
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(toRead);
        
        // Use bulk copy for efficiency
        int oldPos = pcmData.position();
        int oldLimit = pcmData.limit();
        
        pcmData.position(position);
        pcmData.limit(position + toRead);
        buffer.put(pcmData);
        buffer.flip();
        
        // Restore pcmData state
        pcmData.position(oldPos);
        pcmData.limit(oldLimit);
        
        position += toRead;
        return buffer;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (pcmData != null) {
                MemoryUtil.memFree(pcmData);
                pcmData = null;
            }
            StoryAdventureMod.LOGGER.debug("[ExternalOggAudioStream] Closed stream");
        }
    }
    
    /**
     * Check if the stream has more data to read.
     */
    public boolean hasRemaining() {
        return !closed && pcmData != null && position < totalBytes;
    }
    
    /**
     * Get the total size in bytes.
     */
    public int getTotalBytes() {
        return totalBytes;
    }
    
    /**
     * Get current read position.
     */
    public int getPosition() {
        return position;
    }
}