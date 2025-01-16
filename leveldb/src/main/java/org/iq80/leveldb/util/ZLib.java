/*
 * Copyright (C) 2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.iq80.leveldb.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A wrapper for java based ZLib
 */
public final class ZLib
{
    private static final ThreadLocal<Inflater> INFLATER = ThreadLocal.withInitial(Inflater::new);
    private static final ThreadLocal<Inflater> INFLATER_RAW = ThreadLocal.withInitial(() -> new Inflater(true));
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);
    private static final ThreadLocal<Deflater> DEFLATER_RAW = ThreadLocal.withInitial(() -> new Deflater(Deflater.DEFAULT_COMPRESSION, true));

    private ZLib()
    {
    }

    public static ByteBuffer uncompress(ByteBuffer compressed, boolean raw) throws IOException
    {
        Inflater inflater = (raw ? INFLATER_RAW : INFLATER).get();
        try {
            byte[] compressedArray = null;
            int compressedArrayOffset = 0;
            int compressedArraySize = compressed.limit() - compressed.position();
            if (compressed.hasArray() && !compressed.isReadOnly()) {
                compressedArray = compressed.array();
                compressedArrayOffset = compressed.arrayOffset() + compressed.position();
            }
            else {
                compressedArray = new byte[compressedArraySize];
                compressed.get(compressedArray);
            }
            // Guess the uncompressed size. Assuming a compression ratio of 66%
            ByteBuffer buffer = ByteBuffer.allocate((compressedArraySize * 3) / 2);
            inflater.setInput(compressedArray, compressedArrayOffset, compressedArraySize);
            while (!inflater.finished()) {
                int bytesInflated = inflater.inflate(buffer.array(), buffer.position(), buffer.limit() - buffer.position());
                buffer.position(buffer.position() + bytesInflated);
                if (bytesInflated == 0) {
                    // Grow buffer
                    ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() + Math.max(compressedArraySize / 4, 1024));
                    int position = buffer.position();

                    // Reset reader index
                    buffer.flip();
                    newBuffer.put(buffer);

                    // Set position to the original
                    newBuffer.position(position);
                    buffer = newBuffer;
                }
            }

            // Flip buffer
            buffer.flip();
            return buffer;
        }
        catch (DataFormatException e) {
            throw new IOException(e);
        }
        finally {
            inflater.reset();
        }
    }

    public static int compress(byte[] input, int inputOffset, int length, byte[] output, int outputOffset, boolean raw)
            throws IOException
    {
        Deflater deflater = (raw ? DEFLATER_RAW : DEFLATER).get();
        try {
            deflater.setInput(input, inputOffset, length);
            deflater.finish();

            return deflater.deflate(output, outputOffset, output.length - outputOffset);
        }
        finally {
            deflater.reset();
        }
    }
}
