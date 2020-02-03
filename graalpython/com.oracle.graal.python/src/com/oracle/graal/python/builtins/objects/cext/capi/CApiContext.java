/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.CAPIConversionNodeSupplier;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.RefCntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public final class CApiContext extends CExtContext {

    private final ReferenceQueue<Object> nativeObjectsQueue;
    private final LinkedList<Object> stack = new LinkedList<>();
    private Map<Object, Pair<PFrame.Reference, String>> allocatedNativeMemory;

    public CApiContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, CAPIConversionNodeSupplier.INSTANCE);
        nativeObjectsQueue = new ReferenceQueue<>();

        context.registerAsyncAction(() -> {
            Reference<? extends Object> reference = null;
            try {
                reference = nativeObjectsQueue.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (reference instanceof NativeObjectReference) {
                return new CApiReferenceCleanerAction((NativeObjectReference) reference);
            }
            return null;
        });
    }

    public static class NativeObjectReference extends PhantomReference<PythonAbstractNativeObject> {
        private final TruffleObject object;

        public NativeObjectReference(PythonAbstractNativeObject referent, ReferenceQueue<? super PythonAbstractNativeObject> q) {
            super(referent, q);
            this.object = referent.getPtr();
        }

        public TruffleObject getObject() {
            return object;
        }
    }

    public ReferenceQueue<Object> getNativeObjectsQueue() {
        return nativeObjectsQueue;
    }

    private static final class CApiReferenceCleanerAction implements AsyncHandler.AsyncAction {
        private final NativeObjectReference nativeObjectReference;

        public CApiReferenceCleanerAction(NativeObjectReference nativeObjectReference) {
            this.nativeObjectReference = nativeObjectReference;
        }

        @Override
        public void execute(VirtualFrame frame, Node location, RootCallTarget callTarget) {
            PythonLanguage.getContext().getCApiContext().stack.remove(nativeObjectReference);
            RefCntNodeGen.getUncached().dec(nativeObjectReference.object);
        }
    }

    public void createNativeReference(PythonAbstractNativeObject nativeObject) {
        stack.push(new NativeObjectReference(nativeObject, nativeObjectsQueue));
    }

    @TruffleBoundary
    public void traceFree(Object ptr) {
        traceFree(ptr, null, null);
    }

    @TruffleBoundary
    public void traceFree(Object ptr, PFrame.Reference curFrame, String clazzName) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        Object value = allocatedNativeMemory.remove(ptr);
        if (value == null) {
            PythonLanguage.getLogger().severe(String.format("freeing non-allocated memory 0x%X (maybe a double-free?)", ptr));
        }
    }

    @TruffleBoundary
    public void traceAlloc(Object ptr, PFrame.Reference curFrame, String clazzName) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        Object value = allocatedNativeMemory.put(ptr, Pair.create(curFrame, clazzName));
        assert value == null : "native memory allocator reserved same memory twice";
    }

}
