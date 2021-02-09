/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.ints;

import static com.oracle.graal.python.builtins.objects.ints.IntUtils.bigIntToByteArray;
import static com.oracle.graal.python.builtins.objects.ints.IntUtils.longToByteArray;
import static com.oracle.graal.python.builtins.objects.ints.IntUtils.reverse;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class IntNodes {
    // equivalent to _PyLong_Sign
    public abstract static class LongSignNode extends Node {
        public abstract int execute(Object value);

        @Specialization
        int doInt(int value) {
            return Integer.compare(value, 0);
        }

        @Specialization
        int doLong(long value) {
            return Long.compare(value, 0);
        }

        @Specialization
        int doPInt(PInt value) {
            return value.compareTo(0);
        }
    }

    // equivalent to _PyLong_NumBits
    public abstract static class LongNumBitsNode extends Node {
        public abstract int execute(Object value);

        @Specialization
        int doInt(int value) {
            return Integer.bitCount(value);
        }

        @Specialization
        int doLong(long value) {
            return Long.bitCount(value);
        }

        @Specialization
        int doPInt(PInt value) {
            return value.bitCount();
        }
    }

    // equivalent to _PyLong_AsByteArray
    public abstract static class LongAsByteArrayNode extends Node {
        public abstract byte[] execute(Object value, int size, boolean bigEndian);

        @Specialization
        byte[] doLong(long value, int size, boolean bigEndian,
                        @Cached.Shared("isBigEndianProfile") @Cached ConditionProfile isBigEndianProfile) {
            final byte[] bytes = longToByteArray(value, size);
            if (isBigEndianProfile.profile(bigEndian)) {
                reverse(bytes);
            }
            return bytes;
        }

        @Specialization
        byte[] doPInt(PInt value, int size, boolean bigEndian,
                        @Cached.Shared("isBigEndianProfile") @Cached ConditionProfile isBigEndianProfile) {
            final byte[] bytes = bigIntToByteArray(value.getValue(), size);
            if (isBigEndianProfile.profile(bigEndian)) {
                reverse(bytes);
            }
            return bytes;
        }
    }
}
