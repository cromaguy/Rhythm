/*
 ** AlacDecodeUtils.java
 **
 ** Copyright (c) 2011-2014 Peter McQuillan
 **
 ** Based on the ALAC decoder - Copyright (c) 2005 David Hammerton
 **
 ** All rights reserved.
 **
 ** Redistribution and use in source and binary forms, with or without
 ** modification, are permitted provided that the following conditions are met:
 **
 **     * Redistributions of source code must retain the above copyright notice,
 **       this list of conditions and the following disclaimer.
 **     * Redistributions in binary form must reproduce the above copyright notice,
 **       this list of conditions and the following disclaimer in the
 **       documentation and/or other materials provided with the distribution.
 **     * The name of the author may not be used to endorse or promote products
 **       derived from this software without specific prior written permission.
 **
 ** THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 ** AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 ** IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 ** ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
 ** ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 ** DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 ** SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 ** CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 ** OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 ** OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **
 *
 *     Portions: Copyright (C) 2025 nift4
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.beatofthedrum.alacdecoder;

import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;

import org.nift4.alacdecoder.AlacDecoderException;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AlacDecodeUtils {
    private static final int RICE_THRESHOLD = 8;

    public static void alac_set_info(AlacFile alac, ByteBuffer inputbuffer) throws AlacDecoderException {
        alac.setinfo_max_samples_per_frame = inputbuffer.getInt();
        if (alac.setinfo_max_samples_per_frame < 1 || alac.setinfo_max_samples_per_frame > 16364) {
            throw new AlacDecoderException("bad max sample count " + alac.setinfo_max_samples_per_frame);
        }
        int current_version = inputbuffer.get();
        if (current_version != 0) {
            throw new AlacDecoderException("unsupported version " + current_version);
        }
        alac.bitspersample_input = inputbuffer.get();
        if (alac.bitspersample_input != 16 && alac.bitspersample_input != 20 &&
                alac.bitspersample_input != 24 && alac.bitspersample_input != 32) {
            throw new AlacDecoderException("bad sample size " + alac.bitspersample_input);
        }
        alac.setinfo_rice_historymult = inputbuffer.get();
        alac.setinfo_rice_initialhistory = inputbuffer.get();
        alac.setinfo_rice_kmodifier = inputbuffer.get();
        int channel_count = inputbuffer.get();
        if (channel_count < 1 || channel_count > 8) {
            throw new AlacDecoderException("bad channel count " + channel_count);
        }
        // alac.max_run = inputbuffer.getShort();
        alac.max_frame_bytes = inputbuffer.getInt();
        // alac.average_bit_rate = inputbuffer.getInt();
        // alac.sample_rate = inputbuffer.getInt();
    }

    /* stream reading */

    /* supports reading 1 to 16 bits, in big endian format */
    static int readbits_16(AlacFile alac, int bits) {
        int result;
        int new_accumulator;
        int part1;
        int part2 = 0;
        int part3 = 0;

        part1 = (alac.input_buffer[alac.ibIdx] & 0xff);
        if (alac.input_buffer_bitaccumulator + bits > 8) {
            part2 = (alac.input_buffer[alac.ibIdx + 1] & 0xff);
        }
        if (alac.input_buffer_bitaccumulator + bits > 16) {
            part3 = (alac.input_buffer[alac.ibIdx + 2] & 0xff);
        }

        result = ((part1 << 16) | (part2 << 8) | part3);

        /* shift left by the number of bits we've already read,
         * so that the top 'n' bits of the 24 bits we read will
         * be the return bits */
        result = result << alac.input_buffer_bitaccumulator;

        result = result & 0x00ffffff;

        /* and then only want the top 'n' bits from that, where
         * n is 'bits' */
        result = result >> (24 - bits);

        new_accumulator = (alac.input_buffer_bitaccumulator + bits);

        /* increase the buffer pointer if we've read over n bytes. */
        alac.ibIdx += (new_accumulator >> 3);

        /* and the remainder goes back into the bit accumulator */
        alac.input_buffer_bitaccumulator = (new_accumulator & 7);

        return result;
    }

    /* supports reading 1 to 32 bits, in big endian format */
    static int readbits(AlacFile alac, int bits) {
        int result = 0;

        if (bits > 16) {
            bits -= 16;

            result = readbits_16(alac, 16) << bits;
        }

        result |= readbits_16(alac, bits);

        return result;
    }

    /* reads a single bit */
    static int readbit(AlacFile alac) {
        int result;
        int new_accumulator;
        int part1;

        part1 = (alac.input_buffer[alac.ibIdx] & 0xff);

        result = part1;

        result = result << alac.input_buffer_bitaccumulator;

        result = result >> 7 & 1;

        new_accumulator = (alac.input_buffer_bitaccumulator + 1);

        alac.ibIdx += new_accumulator / 8;

        alac.input_buffer_bitaccumulator = (new_accumulator % 8);

        return result;
    }

    static void unreadbits(AlacFile alac, int bits) {
        int new_accumulator = (alac.input_buffer_bitaccumulator - bits);

        alac.ibIdx += (new_accumulator >> 3);

        alac.input_buffer_bitaccumulator = (new_accumulator & 7);
    }

    static void count_leading_zeros_extra(int curbyte, int output, LeadingZeros lz) {

        if ((curbyte & 0xf0) == 0) {
            output += 4;
        } else
            curbyte = curbyte >> 4;

        if ((curbyte & 0x8) != 0) {
            lz.output = output;
            lz.curbyte = curbyte;
            return;
        }
        if ((curbyte & 0x4) != 0) {
            lz.output = output + 1;
            lz.curbyte = curbyte;
            return;
        }
        if ((curbyte & 0x2) != 0) {
            lz.output = output + 2;
            lz.curbyte = curbyte;
            return;
        }
        if ((curbyte & 0x1) != 0) {
            lz.output = output + 3;
            lz.curbyte = curbyte;
            return;
        }

        /* shouldn't get here: */

        lz.output = output + 4;
        lz.curbyte = curbyte;

    }

    static int count_leading_zeros(int input, LeadingZeros lz) {
        int output = 0;
        int curbyte;

        curbyte = input >> 24;
        if (curbyte != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.output;
            return output;
        }
        output += 8;

        curbyte = input >> 16;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.output;

            return output;
        }
        output += 8;

        curbyte = input >> 8;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.output;

            return output;
        }
        output += 8;

        curbyte = input;
        if ((curbyte & 0xFF) != 0) {
            count_leading_zeros_extra(curbyte, output, lz);
            output = lz.output;

            return output;
        }
        output += 8;

        return output;
    }

    public static int entropy_decode_value(AlacFile alac, int readSampleSize, int k, int rice_kmodifier_mask) {
        int x = 0; // decoded value

        // read x, number of 1s before 0 represent the rice value.
        while (x <= RICE_THRESHOLD && readbit(alac) != 0) {
            x++;
        }

        if (x > RICE_THRESHOLD) {
            // read the number from the bit stream (raw value)
            int value;

            value = readbits(alac, readSampleSize);

            // mask value
            value &= ((0xffffffff) >> (32 - readSampleSize));

            x = value;
        } else {
            if (k != 1) {
                int extraBits = readbits(alac, k);

                x *= (((1 << k) - 1) & rice_kmodifier_mask);

                if (extraBits > 1)
                    x += extraBits - 1;
                else
                    unreadbits(alac, 1);
            }
        }

        return x;
    }

    public static void entropy_rice_decode(AlacFile alac, int[] outputBuffer, int outputSize, int readSampleSize, int rice_initialhistory, int rice_kmodifier, int rice_historymult, int rice_kmodifier_mask) {
        int history = rice_initialhistory;
        int outputCount = 0;
        int signModifier = 0;

        while (outputCount < outputSize) {
            int decodedValue;
            int finalValue;
            int k;

            k = 31 - rice_kmodifier - count_leading_zeros((history >> 9) + 3, alac.lz);

            if (k < 0)
                k += rice_kmodifier;
            else
                k = rice_kmodifier;

            // note: don't use rice_kmodifier_mask here (set mask to 0xFFFFFFFF)
            decodedValue = entropy_decode_value(alac, readSampleSize, k, 0xFFFFFFFF);

            decodedValue += signModifier;
            finalValue = ((decodedValue + 1) / 2); // inc by 1 and shift out sign bit
            if ((decodedValue & 1) != 0) // the sign is stored in the low bit
                finalValue *= -1;

            outputBuffer[outputCount] = finalValue;

            signModifier = 0;

            // update history
            history += (decodedValue * rice_historymult) - ((history * rice_historymult) >> 9);

            if (decodedValue > 0xFFFF)
                history = 0xFFFF;

            // special case, for compressed blocks of 0
            if ((history < 128) && (outputCount + 1 < outputSize)) {
                int blockSize;

                signModifier = 1;

                k = count_leading_zeros(history, alac.lz) + ((history + 16) / 64) - 24;

                // note: blockSize is always 16bit
                blockSize = entropy_decode_value(alac, 16, k, rice_kmodifier_mask);

                // got blockSize 0s
                if (blockSize > 0) {
                    int countSize;
                    countSize = blockSize;
                    for (int j = 0; j < countSize; j++) {
                        outputBuffer[outputCount + 1 + j] = 0;
                    }
                    outputCount += blockSize;
                }

                if (blockSize > 0xFFFF)
                    signModifier = 0;

                history = 0;
            }

            outputCount++;
        }
    }

    static void predictor_decompress_fir_adapt(int[] error_buffer, int output_size, int readsamplesize, int[] predictor_coef_table, int predictor_coef_num, int predictor_quantitization) {
        int buffer_out_idx;
        int[] buffer_out;
        int bitsmove;

        /* first sample always copies */
        buffer_out = error_buffer;

        if (predictor_coef_num == 0) {
            if (output_size <= 1)
                return;
            int sizeToCopy;
            sizeToCopy = (output_size - 1) * 4;
            System.arraycopy(error_buffer, 1, buffer_out, 1, sizeToCopy);
            return;
        }

        if (predictor_coef_num == 0x1f) // 11111 - max value of predictor_coef_num
        {
            /* second-best case scenario for fir decompression,
             * error describes a small difference from the previous sample only
             */
            if (output_size <= 1)
                return;

            for (int i = 0; i < (output_size - 1); i++) {
                int prev_value;
                int error_value;

                prev_value = buffer_out[i];
                error_value = error_buffer[i + 1];

                bitsmove = 32 - readsamplesize;
                buffer_out[i + 1] = (((prev_value + error_value) << bitsmove) >> bitsmove);
            }
            return;
        }

        /* read warm-up samples */
        if (predictor_coef_num > 0) {
            for (int i = 0; i < predictor_coef_num; i++) {
                int val;

                val = buffer_out[i] + error_buffer[i + 1];

                bitsmove = 32 - readsamplesize;

                val = ((val << bitsmove) >> bitsmove);

                buffer_out[i + 1] = val;
            }
        }

        /* general case */
        if (predictor_coef_num > 0) {
            buffer_out_idx = 0;
            for (int i = predictor_coef_num + 1; i < output_size; i++) {
                int j;
                int sum = 0;
                int outval;
                int error_val = error_buffer[i];

                for (j = 0; j < predictor_coef_num; j++) {
                    sum += (buffer_out[buffer_out_idx + predictor_coef_num - j] - buffer_out[buffer_out_idx]) * predictor_coef_table[j];
                }

                outval = (1 << (predictor_quantitization - 1)) + sum;
                outval = outval >> predictor_quantitization;
                outval = outval + buffer_out[buffer_out_idx] + error_val;
                bitsmove = 32 - readsamplesize;

                outval = ((outval << bitsmove) >> bitsmove);

                buffer_out[buffer_out_idx + predictor_coef_num + 1] = outval;

                if (error_val > 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val > 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = Integer.compare(val, 0);

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // absolute value

                        error_val -= ((val >> predictor_quantitization) * (predictor_coef_num - predictor_num));

                        predictor_num--;
                    }
                } else if (error_val < 0) {
                    int predictor_num = predictor_coef_num - 1;

                    while (predictor_num >= 0 && error_val < 0) {
                        int val = buffer_out[buffer_out_idx] - buffer_out[buffer_out_idx + predictor_coef_num - predictor_num];
                        int sign = -Integer.compare(val, 0);

                        predictor_coef_table[predictor_num] -= sign;

                        val *= sign; // neg value

                        error_val -= ((val >> predictor_quantitization) * (predictor_coef_num - predictor_num));

                        predictor_num--;
                    }
                }

                buffer_out_idx++;
            }
        }
    }


    public static void deinterlace_16(int[] buffer_a, int[] buffer_b, ByteBuffer buffer_out, int numchannels, int channel_index_a, int channel_index_b, int numsamples, int interlacing_shift, int interlacing_leftweight) {

        if (numsamples <= 0)
            return;

        /* weighted interlacing */
        if (0 != interlacing_leftweight) {
            for (int i = 0; i < numsamples; i++) {
                int difference;
                int midright;
                int left;
                int right;

                midright = buffer_a[i];
                difference = buffer_b[i];

                right = (midright - ((difference * interlacing_leftweight) >> interlacing_shift));
                left = (right + difference);

                /* output is always little endian */

                buffer_out.putShort((i * numchannels + channel_index_a) * 2, (short) left);
                buffer_out.putShort((i * numchannels + channel_index_b) * 2, (short) right);
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numsamples; i++) {
            int left;
            int right;

            left = buffer_a[i];
            right = buffer_b[i];

            /* output is always little endian */

            buffer_out.putShort((i * numchannels + channel_index_a) * 2, (short) left);
            buffer_out.putShort((i * numchannels + channel_index_b) * 2, (short) right);
        }
    }


    public static void deinterlace_24(int bitspersample, int[] buffer_a, int[] buffer_b, int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, ByteBuffer buffer_out, int numchannels, int channel_index_a, int channel_index_b, int numsamples, int interlacing_shift, int interlacing_leftweight) {
        if (numsamples <= 0)
            return;

        /* weighted interlacing */
        if (interlacing_leftweight != 0) {
            for (int i = 0; i < numsamples; i++) {
                int difference;
                int midright;
                int left;
                int right;

                midright = buffer_a[i];
                difference = buffer_b[i];

                right = midright - ((difference * interlacing_leftweight) >> interlacing_shift);
                left = right + difference;

                if (bitspersample == 20) {
                    left = left << 4;
                    right = right << 4;
                }

                if (bitspersample == 24 && uncompressed_bytes != 0 && uncompressed_bytes_buffer_a != null) {
                    int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                    left <<= (uncompressed_bytes * 8);
                    right <<= (uncompressed_bytes * 8);

                    left = left | (uncompressed_bytes_buffer_a[i] & mask);
                    right = right | (uncompressed_bytes_buffer_b[i] & mask);
                }

                buffer_out.position((i * numchannels + channel_index_a) * 3);
                Util.putInt24(buffer_out, (left << 8) >> 8);
                buffer_out.position((i * numchannels + channel_index_b) * 3);
                Util.putInt24(buffer_out, (right << 8) >> 8);
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numsamples; i++) {
            int left;
            int right;

            left = buffer_a[i];
            if (bitspersample == 20) {
                left = left << 4;
            }
            right = buffer_b[i];
            if (bitspersample == 20) {
                right = right << 4;
            }

            if (bitspersample == 24 && uncompressed_bytes != 0 && uncompressed_bytes_buffer_a != null) {
                int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                left <<= (uncompressed_bytes * 8);
                right <<= (uncompressed_bytes * 8);

                left = left | (uncompressed_bytes_buffer_a[i] & mask);
                right = right | (uncompressed_bytes_buffer_b[i] & mask);
            }

            buffer_out.position((i * numchannels + channel_index_a) * 3);
            Util.putInt24(buffer_out, (left << 8) >> 8);
            buffer_out.position((i * numchannels + channel_index_b) * 3);
            Util.putInt24(buffer_out, (right << 8) >> 8);

        }

    }


    public static void deinterlace_32(int[] buffer_a, int[] buffer_b, int uncompressed_bytes, int[] uncompressed_bytes_buffer_a, int[] uncompressed_bytes_buffer_b, ByteBuffer buffer_out, int numchannels, int channel_index_a, int channel_index_b, int numsamples, int interlacing_shift, int interlacing_leftweight) {
        if (numsamples <= 0)
            return;

        /* weighted interlacing */
        if (interlacing_leftweight != 0) {
            for (int i = 0; i < numsamples; i++) {
                int difference;
                int midright;
                int left;
                int right;

                midright = buffer_a[i];
                difference = buffer_b[i];

                right = midright - ((difference * interlacing_leftweight) >> interlacing_shift);
                left = right + difference;

                if (uncompressed_bytes != 0) {
                    int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                    left <<= (uncompressed_bytes * 8);
                    right <<= (uncompressed_bytes * 8);

                    left = left | (uncompressed_bytes_buffer_a[i] & mask);
                    right = right | (uncompressed_bytes_buffer_b[i] & mask);
                }

                buffer_out.putInt((i * numchannels + channel_index_a) * 4, left);
                buffer_out.putInt((i * numchannels + channel_index_b) * 4, right);
            }

            return;
        }

        /* otherwise basic interlacing took place */
        for (int i = 0; i < numsamples; i++) {
            int left;
            int right;

            left = buffer_a[i];
            right = buffer_b[i];

            if (uncompressed_bytes != 0) {
                int mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                left <<= (uncompressed_bytes * 8);
                right <<= (uncompressed_bytes * 8);

                left = left | (uncompressed_bytes_buffer_a[i] & mask);
                right = right | (uncompressed_bytes_buffer_b[i] & mask);
            }

            buffer_out.putInt((i * numchannels + channel_index_a) * 4, left);
            buffer_out.putInt((i * numchannels + channel_index_b) * 4, right);
        }

    }


    public static int decode_frame(AlacFile alac, DecoderInputBuffer decinbuffer, SimpleDecoderOutputBuffer decoutbuffer) throws AlacDecoderException {
        int outputsize = 0;
        int channel_index = 0;
        int[] tags = new int[3];

        /* setup the stream */
        alac.input_buffer = Util.castNonNull(decinbuffer.data).array();
        alac.input_buffer_bitaccumulator = 0;
        alac.ibIdx = 0;

        while (true) {
            int frame_type;
            int outputsamples = alac.setinfo_max_samples_per_frame;
            int newoutputsize;
            if (alac.ibIdx >= alac.input_buffer.length || (alac.input_buffer_bitaccumulator > 5 &&
                    alac.ibIdx + 1 >= alac.input_buffer.length)) {
                Log.w("AlacDecoder", "no space for frame tag and no end tag yet!");
                break;
            }
            frame_type = readbits(alac, 3);
            if (frame_type == 0 && channel_index >= alac.channel_map.length) {
                int idx = alac.ibIdx * 8 + alac.input_buffer_bitaccumulator;
                if (alac.input_buffer_bitaccumulator > 0) {
                    int cnt = 8 - alac.input_buffer_bitaccumulator;
                    int bits = readbits(alac, cnt);
                    if (bits != 0) {
                        throw new AlacDecoderException("found data " + bits + " in align of " + cnt + " bits after zeroed frame");
                    }
                }
                for (int i = alac.ibIdx; i < alac.input_buffer.length; i++) {
                    int read = readbits(alac, 8);
                    if (read != 0) {
                        throw new AlacDecoderException("found data " + read + " at " + alac.ibIdx + " bytes, had zeroed frame");
                    }
                }
                Log.w("AlacDecoder", "missing end frame and " +
                        (alac.input_buffer.length * 8 - idx) + " zero bits, file is watermarked");
                break;
            }

            if (frame_type == 0 || frame_type == 1 || frame_type == 3) {
                int tag = readbits(alac, 4);
                int expected = tags[frame_type == 3 ? 2 : frame_type]++;
                if (expected != tag) {
                    throw new AlacDecoderException("bad channel tag " + tag + ", wanted " + expected);
                }
                int data = readbits(alac, 12);
                if (data != 0) {
                    throw new AlacDecoderException("hidden data " + data + ", wanted 0");
                }
            }

            if (frame_type == 0 || frame_type == 3) // 1 channel
            {
                int hassize;
                int isnotcompressed;
                int readsamplesize;

                int uncompressed_bytes;
                int ricemodifier;

                int tempPred;
                int channel_index_a = alac.channel_map[channel_index];

                hassize = readbits(alac, 1); // the output sample size is stored soon

                uncompressed_bytes = readbits(alac, 2); // number of bytes in the (compressed) stream that are not compressed

                isnotcompressed = readbits(alac, 1); // whether the frame is compressed

                if (hassize != 0) {
                    /* now read the number of samples,
                     * as a 32bit integer */
                    outputsamples = readbits(alac, 32);
                }
                newoutputsize = outputsamples * alac.bytespersample_output;
                if (newoutputsize > outputsize) {
                    outputsize = newoutputsize;
                }

                if (decoutbuffer.data == null) {
                    decoutbuffer.init(decinbuffer.timeUs, outputsize);
                } else if (outputsize > decoutbuffer.data.limit()) {
                    Log.w("AlacDecoder", "had to grow buffer from " +
                            decoutbuffer.data.limit() + " to " + outputsize + ", shouldn't happen");
                    decoutbuffer.grow(outputsize);
                }
                ByteBuffer outbuffer = Util.castNonNull(decoutbuffer.data);

                readsamplesize = alac.bitspersample_input - (uncompressed_bytes * 8);

                if (isnotcompressed == 0) { // so it is compressed
                    int[] predictor_coef_table = alac.predictor_coef_table_a;
                    int predictor_coef_num;
                    int prediction_type;
                    int prediction_quantitization;
                    int i;

                    /* skip 16 bits only useful in two channel case */
                    int interlacing_shift = readbits(alac, 8);
                    if (interlacing_shift != 0) {
                        throw new AlacDecoderException("invalid interlacing shift for mono: " + interlacing_shift);
                    }
                    int interlacing_leftweight = readbits(alac, 8);
                    if (interlacing_leftweight != 0) {
                        throw new AlacDecoderException("invalid interlacing left weight for mono: " + interlacing_leftweight);
                    }

                    prediction_type = readbits(alac, 4);
                    prediction_quantitization = readbits(alac, 4);

                    ricemodifier = readbits(alac, 3);
                    predictor_coef_num = readbits(alac, 5);

                    /* read the predictor table */

                    for (i = 0; i < predictor_coef_num; i++) {
                        tempPred = readbits(alac, 16);
                        if (tempPred > 32767) {
                            // the predictor coef table values are only 16 bit signed
                            tempPred = tempPred - 65536;
                        }

                        predictor_coef_table[i] = tempPred;
                    }

                    if (uncompressed_bytes != 0) {
                        for (i = 0; i < outputsamples; i++) {
                            int result = readbits(alac, uncompressed_bytes * 8);
                            if (alac.uncompressed_bytes_buffer_a != null) {
                                alac.uncompressed_bytes_buffer_a[i] = result;
                            }
                        }
                    }


                    entropy_rice_decode(alac, alac.outputsamples_buffer[channel_index], outputsamples, readsamplesize, alac.setinfo_rice_initialhistory, alac.setinfo_rice_kmodifier, ricemodifier * (alac.setinfo_rice_historymult / 4), (1 << alac.setinfo_rice_kmodifier) - 1);

                    if (prediction_type != 0) {
                        predictor_decompress_fir_adapt(alac.outputsamples_buffer[channel_index], outputsamples, readsamplesize, predictor_coef_table, 31, 0);
                    }
                    predictor_decompress_fir_adapt(alac.outputsamples_buffer[channel_index], outputsamples, readsamplesize, predictor_coef_table, predictor_coef_num, prediction_quantitization);
                } else { // not compressed, easy case
                    if (alac.bitspersample_input <= 16) {
                        int bitsmove;
                        for (int i = 0; i < outputsamples; i++) {
                            int audiobits = readbits(alac, alac.bitspersample_input);
                            bitsmove = 32 - alac.bitspersample_input;

                            audiobits = ((audiobits << bitsmove) >> bitsmove);

                            alac.outputsamples_buffer[channel_index][i] = audiobits;
                        }
                    } else {
                        int x;
                        int m = 1 << (alac.bitspersample_input - 1);
                        for (int i = 0; i < outputsamples; i++) {
                            int audiobits;

                            audiobits = readbits(alac, 16);
                            /* special case of sign extension..
                             * as we'll be ORing the low 16bits into this */
                            audiobits = audiobits << (alac.bitspersample_input - 16);
                            audiobits = audiobits | readbits(alac, alac.bitspersample_input - 16);
                            if (alac.bitspersample_input != 32) {
                                x = audiobits & ((1 << alac.bitspersample_input) - 1);
                                audiobits = (x ^ m) - m;    // sign extend our data bits
                            }

                            alac.outputsamples_buffer[channel_index][i] = audiobits;
                        }
                    }
                    uncompressed_bytes = 0; // always 0 for uncompressed
                }

                switch (alac.bitspersample_input) {
                    case 16: {
                        for (int i = 0; i < outputsamples; i++) {
                            short sample = (short) alac.outputsamples_buffer[channel_index][i];
                            outbuffer.putShort((i * alac.numchannels + channel_index_a) * 2, sample);
                        }
                        break;
                    }
                    case 20:
                        for (int i = 0; i < outputsamples; i++) {
                            int sample = alac.outputsamples_buffer[channel_index][i] << 4;

                            outbuffer.position((i * alac.numchannels + channel_index_a) * 3);
                            Util.putInt24(outbuffer, (sample << 8) >> 8);
                        }
                        break;
                    case 24: {
                        for (int i = 0; i < outputsamples; i++) {
                            int sample = alac.outputsamples_buffer[channel_index][i];

                            if (uncompressed_bytes != 0) {
                                int mask;
                                sample = sample << (uncompressed_bytes * 8);
                                mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                                sample = sample | (alac.uncompressed_bytes_buffer_a[i] & mask);
                            }

                            outbuffer.position((i * alac.numchannels + channel_index_a) * 3);
                            Util.putInt24(outbuffer, (sample << 8) >> 8);

                        }
                        break;
                    }
                    case 32: {
                        for (int i = 0; i < outputsamples; i++) {
                            int sample = alac.outputsamples_buffer[channel_index][i];

                            if (uncompressed_bytes != 0) {
                                int mask;
                                sample = sample << (uncompressed_bytes * 8);
                                mask = ~(0xFFFFFFFF << (uncompressed_bytes * 8));
                                sample = sample | (alac.uncompressed_bytes_buffer_a[i] & mask);
                            }

                            outbuffer.putInt((i * alac.numchannels + channel_index_a) * 4, sample);

                        }
                        break;
                    }
                }
                channel_index++;
            } else if (frame_type == 1) // 2 channels
            {
                int hassize;
                int isnotcompressed;
                int readsamplesize;

                int uncompressed_bytes;

                int interlacing_shift;
                int interlacing_leftweight;
                int channel_index_a = alac.channel_map[channel_index];
                int channel_index_b = alac.channel_map[channel_index + 1];

                hassize = readbits(alac, 1); // the output sample size is stored soon

                uncompressed_bytes = readbits(alac, 2); // the number of bytes in the (compressed) stream that are not compressed

                isnotcompressed = readbits(alac, 1); // whether the frame is compressed

                if (hassize != 0) {
                    /* now read the number of samples,
                     * as a 32bit integer */
                    outputsamples = readbits(alac, 32);
                }
                newoutputsize = outputsamples * alac.bytespersample_output;
                if (newoutputsize > outputsize) {
                    outputsize = newoutputsize;
                }

                if (decoutbuffer.data == null) {
                    decoutbuffer.init(decinbuffer.timeUs, outputsize);
                } else if (outputsize > decoutbuffer.data.limit()) {
                    Log.w("AlacDecoder", "had to grow buffer from " +
                            decoutbuffer.data.limit() + " to " + outputsize + ", shouldn't happen");
                    decoutbuffer.grow(outputsize);
                }
                ByteBuffer outbuffer = Util.castNonNull(decoutbuffer.data);

                readsamplesize = alac.bitspersample_input - (uncompressed_bytes * 8) + 1;

                if (isnotcompressed == 0) { // compressed
                    int[] predictor_coef_table_a = alac.predictor_coef_table_a;
                    int predictor_coef_num_a;
                    int prediction_type_a;
                    int prediction_quantitization_a;
                    int ricemodifier_a;

                    int[] predictor_coef_table_b = alac.predictor_coef_table_b;
                    int predictor_coef_num_b;
                    int prediction_type_b;
                    int prediction_quantitization_b;
                    int ricemodifier_b;

                    int tempPred;

                    interlacing_shift = readbits(alac, 8);
                    interlacing_leftweight = readbits(alac, 8);

                    /* ******* channel 1 ***********/
                    prediction_type_a = readbits(alac, 4);
                    prediction_quantitization_a = readbits(alac, 4);

                    ricemodifier_a = readbits(alac, 3);
                    predictor_coef_num_a = readbits(alac, 5);

                    /* read the predictor table */

                    for (int i = 0; i < predictor_coef_num_a; i++) {
                        tempPred = readbits(alac, 16);
                        if (tempPred > 32767) {
                            // the predictor coef table values are only 16 bit signed
                            tempPred = tempPred - 65536;
                        }
                        predictor_coef_table_a[i] = tempPred;
                    }

                    /* ******* channel 2 *********/
                    prediction_type_b = readbits(alac, 4);
                    prediction_quantitization_b = readbits(alac, 4);

                    ricemodifier_b = readbits(alac, 3);
                    predictor_coef_num_b = readbits(alac, 5);

                    /* read the predictor table */

                    for (int i = 0; i < predictor_coef_num_b; i++) {
                        tempPred = readbits(alac, 16);
                        if (tempPred > 32767) {
                            // the predictor coef table values are only 16 bit signed
                            tempPred = tempPred - 65536;
                        }
                        predictor_coef_table_b[i] = tempPred;
                    }

                    /* ********************/
                    if (uncompressed_bytes != 0) { // see mono case
                        for (int i = 0; i < outputsamples; i++) {
                            int result_a = readbits(alac, uncompressed_bytes * 8);
                            int result_b = readbits(alac, uncompressed_bytes * 8);
                            if (alac.uncompressed_bytes_buffer_a != null) {
                                alac.uncompressed_bytes_buffer_a[i] = result_a;
                                alac.uncompressed_bytes_buffer_b[i] = result_b;
                            }
                        }
                    }

                    /* channel 1 */

                    entropy_rice_decode(alac, alac.outputsamples_buffer[channel_index], outputsamples, readsamplesize, alac.setinfo_rice_initialhistory, alac.setinfo_rice_kmodifier, ricemodifier_a * (alac.setinfo_rice_historymult / 4), (1 << alac.setinfo_rice_kmodifier) - 1);
                    if (prediction_type_a != 0) {
                        predictor_decompress_fir_adapt(alac.outputsamples_buffer[channel_index], outputsamples, readsamplesize, predictor_coef_table_a, 31, 0);
                    }
                    predictor_decompress_fir_adapt(alac.outputsamples_buffer[channel_index], outputsamples, readsamplesize, predictor_coef_table_a, predictor_coef_num_a, prediction_quantitization_a);


                    /* channel 2 */
                    entropy_rice_decode(alac, alac.outputsamples_buffer[channel_index + 1], outputsamples, readsamplesize, alac.setinfo_rice_initialhistory, alac.setinfo_rice_kmodifier, ricemodifier_b * (alac.setinfo_rice_historymult / 4), (1 << alac.setinfo_rice_kmodifier) - 1);
                    if (prediction_type_b != 0) {
                        predictor_decompress_fir_adapt(alac.outputsamples_buffer[channel_index + 1], outputsamples, readsamplesize, predictor_coef_table_b, 31, 0);
                    }
                    predictor_decompress_fir_adapt(alac.outputsamples_buffer[channel_index + 1], outputsamples, readsamplesize, predictor_coef_table_b, predictor_coef_num_b, prediction_quantitization_b);
                } else { // not compressed, easy case
                    if (alac.bitspersample_input <= 16) {
                        int bitsmove;

                        for (int i = 0; i < outputsamples; i++) {
                            int audiobits_a;
                            int audiobits_b;

                            audiobits_a = readbits(alac, alac.bitspersample_input);
                            audiobits_b = readbits(alac, alac.bitspersample_input);

                            bitsmove = 32 - alac.bitspersample_input;

                            audiobits_a = ((audiobits_a << bitsmove) >> bitsmove);
                            audiobits_b = ((audiobits_b << bitsmove) >> bitsmove);

                            alac.outputsamples_buffer[channel_index][i] = audiobits_a;
                            alac.outputsamples_buffer[channel_index + 1][i] = audiobits_b;
                        }
                    } else {
                        int x;
                        int m = 1 << (alac.bitspersample_input - 1);

                        for (int i = 0; i < outputsamples; i++) {
                            int audiobits_a;
                            int audiobits_b;

                            audiobits_a = readbits(alac, 16);
                            audiobits_a = audiobits_a << (alac.bitspersample_input - 16);
                            audiobits_a = audiobits_a | readbits(alac, alac.bitspersample_input - 16);
                            if (alac.bitspersample_input != 32) {
                                x = audiobits_a & ((1 << alac.bitspersample_input) - 1);
                                audiobits_a = (x ^ m) - m;        // sign extend our data bits
                            }

                            audiobits_b = readbits(alac, 16);
                            audiobits_b = audiobits_b << (alac.bitspersample_input - 16);
                            audiobits_b = audiobits_b | readbits(alac, alac.bitspersample_input - 16);
                            if (alac.bitspersample_input != 32) {
                                x = audiobits_b & ((1 << alac.bitspersample_input) - 1);
                                audiobits_b = (x ^ m) - m;        // sign extend our data bits
                            }

                            alac.outputsamples_buffer[channel_index][i] = audiobits_a;
                            alac.outputsamples_buffer[channel_index + 1][i] = audiobits_b;
                        }
                    }
                    uncompressed_bytes = 0; // always 0 for uncompressed
                    interlacing_shift = 0;
                    interlacing_leftweight = 0;
                }

                switch (alac.bitspersample_input) {
                    case 16: {
                        deinterlace_16(alac.outputsamples_buffer[channel_index], alac.outputsamples_buffer[channel_index + 1], outbuffer, alac.numchannels, channel_index_a, channel_index_b, outputsamples, interlacing_shift, interlacing_leftweight);
                        break;
                    }
                    case 20: // output 20-bit input as 24-bit output
                    case 24: {
                        deinterlace_24(alac.bitspersample_input, alac.outputsamples_buffer[channel_index], alac.outputsamples_buffer[channel_index + 1], uncompressed_bytes, alac.uncompressed_bytes_buffer_a, alac.uncompressed_bytes_buffer_b, outbuffer, alac.numchannels, channel_index_a, channel_index_b, outputsamples, interlacing_shift, interlacing_leftweight);
                        break;
                    }
                    case 32: {
                        deinterlace_32(alac.outputsamples_buffer[channel_index], alac.outputsamples_buffer[channel_index + 1], uncompressed_bytes, alac.uncompressed_bytes_buffer_a, alac.uncompressed_bytes_buffer_b, outbuffer, alac.numchannels, channel_index_a, channel_index_b, outputsamples, interlacing_shift, interlacing_leftweight);
                        break;
                    }
                }
                channel_index += 2;
            } else if (frame_type == 4) {
                int size = readbits(alac, 4);
                if (size == 15)
                    size += readbits(alac, 8) - 1;
                if (size > 0) {
                    int type = readbits(alac, 4);
                    if (type != 1) {
                        throw new AlacDecoderException("unsupported filler type " + type);
                    }
                    int fill_nibble = readbits(alac, 4);
                    if (fill_nibble != 0) {
                        throw new AlacDecoderException("unsupported filler nibble " + fill_nibble);
                    }
                }
                for (int i = 0; i < size - 1; i++) {
                    int data = readbits(alac, 8);
                    if (data != 0xa5) {
                        throw new AlacDecoderException("found non-filler data " + data);
                    }
                }
                Log.w("AlacDecoder", "got " + size + " bytes of filler");
            } else if (frame_type == 6) {
                int tag = readbits(alac, 4);
                boolean filler = tag == 0;
                boolean align = readbit(alac) != 0;
                int size = readbits(alac, 8);
                if (size == 255)
                    size += readbits(alac, 8);
                if (align && alac.input_buffer_bitaccumulator > 0) {
                    int cnt = 8 - alac.input_buffer_bitaccumulator;
                    int bits = readbits(alac, cnt);
                    if (bits != 0) {
                        throw new AlacDecoderException("found data " + bits + " in align of " + cnt + " bits");
                    }
                } else if (!align) {
                    // encoder doesn't do this and we're paranoid.
                    filler = false;
                }
                int[] buffer = new int[size];
                for (int i = 0; i < size; i++) {
                    buffer[i] = readbits(alac, 8);
                    if (buffer[i] != 0x5a) {
                        filler = false;
                    }
                }
                if (filler) {
                    Log.w("AlacDecoder", "got " + size + " bytes of filler DSE, " +
                            "debug encoder used");
                } else {
                    Log.w("AlacDecoder", "got DSE: " + Arrays.toString(buffer));
                }
            } else if (frame_type == 2 || frame_type == 5) {
                throw new AlacDecoderException("refalac does not support tag " + frame_type + ", is this file corrupt? or is there a new version of ALAC?");
            } else if (frame_type == 7) {
                break; // stream ending
            } else {
                throw new AlacDecoderException("invalid tag " + frame_type);
            }
        }
        int length = decinbuffer.data.limit();
        if (alac.input_buffer_bitaccumulator > 0) {
            int cnt = 8 - alac.input_buffer_bitaccumulator;
            int bits = readbits(alac, cnt);
            if (bits != 0) {
                throw new AlacDecoderException("found trailing data " + bits + " in last " + cnt
                        + " bits (" + (length - alac.ibIdx) + " bytes trailing)");
            }
        }
        if (alac.ibIdx < length) {
            throw new AlacDecoderException("found " + (length - alac.ibIdx) + " bytes trailing");
        }
        return outputsize;
    }

    public static AlacFile create_alac(int samplesize, int numchannels) {
        AlacFile newfile = new AlacFile();

        if (samplesize >= 24) {
            newfile.uncompressed_bytes_buffer_a = new int[newfile.buffer_size];
            if (numchannels > 1) {
                newfile.uncompressed_bytes_buffer_b = new int[newfile.buffer_size];
            }
        }
        newfile.outputsamples_buffer = new int[numchannels][];
        for (int i = 0; i < numchannels; i++) {
            newfile.outputsamples_buffer[i] = new int[newfile.buffer_size];
        }
        if (numchannels > 1) {
            newfile.predictor_coef_table_b = new int[1024];
        }
        newfile.numchannels = numchannels;
        newfile.bytespersample_output = ((samplesize == 20 ? 24 : samplesize) / 8) * numchannels;

        return newfile;
    }
}

