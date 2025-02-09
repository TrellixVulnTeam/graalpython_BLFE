/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

/**
 * Obtains a string representation of a function for error reporting. Equivalent of CPython's
 * {@code _PyObject_FunctionStr}.
 */
@GenerateUncached
public abstract class PyObjectFunctionStr extends PNodeWithContext {
    public abstract TruffleString execute(Frame frame, Object function);

    @Specialization
    TruffleString str(VirtualFrame frame, Object function,
                    @Cached PyObjectLookupAttr lookupName,
                    @Cached PyObjectStrAsTruffleStringNode asStr,
                    @Cached TruffleString.ConcatNode concatNode) {
        return concatNode.execute(asStr.execute(lookupName.execute(frame, function, T___NAME__)), T_EMPTY_PARENS, TS_ENCODING, false);
        // The following code is for 3.11
        // Object qualname = lookupQualname.execute(frame, function, __QUALNAME__);
        // if (qualname == PNone.NO_VALUE) {
        // return asStr.execute(function);
        // }
        // Object module = lookupModule.execute(frame, function, __MODULE__);
        // if (!(module instanceof PNone)) {
        // String moduleStr = asStr.execute(frame, module);
        // if (!"builtins".equals(moduleStr)) {
        // return PString.cat(moduleStr, ".", asStr.execute(frame, qualname), "()");
        // }
        // }
        // return PString.cat(asStr.execute(frame, qualname), "()");
    }

    public static PyObjectFunctionStr create() {
        return PyObjectFunctionStrNodeGen.create();
    }

    public static PyObjectFunctionStr getUncached() {
        return PyObjectFunctionStrNodeGen.getUncached();
    }
}
