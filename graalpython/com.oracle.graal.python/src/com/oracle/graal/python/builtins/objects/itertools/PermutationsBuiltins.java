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
package com.oracle.graal.python.builtins.objects.itertools;

import com.oracle.graal.python.annotations.ArgumentClinic;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_INT_AS_R;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEGATIVE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PPermutations})
public final class PermutationsBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PermutationsBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "iterable", "r"})
    @ArgumentClinic(name = "r", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PermutationsBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"isNone(r)"})
        Object init(VirtualFrame frame, PPermutations self, Object iterable, @SuppressWarnings("unused") PNone r,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached ConditionProfile nrProfile,
                        @Cached LoopConditionProfile indicesLoopProfile,
                        @Cached LoopConditionProfile cyclesLoopProfile) {
            int len = sizeNode.execute(frame, iterable);
            init(self, iterable, len, len, nrProfile, indicesLoopProfile, cyclesLoopProfile);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNone(rArg)"})
        Object init(VirtualFrame frame, PPermutations self, Object iterable, Object rArg,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached CastToJavaIntExactNode castToInt,
                        @Cached BranchProfile wrongRprofile,
                        @Cached BranchProfile negRprofile,
                        @Cached ConditionProfile nrProfile,
                        @Cached LoopConditionProfile indicesLoopProfile,
                        @Cached LoopConditionProfile cyclesLoopProfile) {
            int r;
            try {
                r = castToInt.execute(rArg);
            } catch (CannotCastException e) {
                wrongRprofile.enter();
                throw raise(TypeError, EXPECTED_INT_AS_R);
            }
            if (r < 0) {
                negRprofile.enter();
                throw raise(ValueError, MUST_BE_NON_NEGATIVE, "r");
            }
            int len = sizeNode.execute(frame, iterable);
            init(self, iterable, r, len, nrProfile, indicesLoopProfile, cyclesLoopProfile);
            return PNone.NONE;
        }

        public static void init(PPermutations self, Object iterable, int r, int n, ConditionProfile nrProfile, LoopConditionProfile indicesLoopProfile, LoopConditionProfile cyclesLoopProfile) {
            self.setPool(iterable);
            self.setR(r);
            self.setN(n);
            int nMinusR = n - r;
            if (nrProfile.profile(nMinusR < 0)) {
                self.setStopped(true);
                self.setRaisedStopIteration(true);
            } else {
                self.setStopped(false);
                int[] indices = new int[n];
                indicesLoopProfile.profileCounted(indices.length);
                for (int i = 0; indicesLoopProfile.inject(i < indices.length); i++) {
                    indices[i] = i;
                }
                self.setIndices(indices);
                int[] cycles = new int[r];
                int idx = 0;
                cyclesLoopProfile.profileCounted(nMinusR);
                for (int i = n; cyclesLoopProfile.inject(i > nMinusR); i--) {
                    cycles[idx++] = i;
                }
                self.setCycles(cycles);
                self.setRaisedStopIteration(false);
                self.setStarted(false);
            }
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PPermutations self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isStopped()")
        Object next(PPermutations self) {
            self.setRaisedStopIteration(true);
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isStopped()")
        Object next(VirtualFrame frame, PPermutations self,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached ConditionProfile isStartedProfile,
                        @Cached BranchProfile jProfile,
                        @Cached LoopConditionProfile resultLoopProfile,
                        @Cached LoopConditionProfile mainLoopProfile,
                        @Cached LoopConditionProfile shiftIndicesProfile) {
            int r = self.getR();

            int[] indices = self.getIndices();
            Object[] result = new Object[r];
            Object pool = self.getPool();
            resultLoopProfile.profileCounted(r);
            for (int i = 0; resultLoopProfile.inject(i < r); i++) {
                result[i] = getItemNode.execute(frame, pool, indices[i]);
            }

            int[] cycles = self.getCycles();
            int i = r - 1;
            while (mainLoopProfile.profile(i >= 0)) {
                int j = cycles[i] - 1;
                if (j > 0) {
                    jProfile.enter();
                    cycles[i] = j;
                    int tmp = indices[i];
                    indices[i] = indices[indices.length - j];
                    indices[indices.length - j] = tmp;
                    return factory().createTuple(result);
                }
                cycles[i] = indices.length - i;
                int n1 = indices.length - 1;
                assert n1 >= 0;
                int num = indices[i];
                shiftIndicesProfile.profileCounted(n1 - i);
                for (int k = i; shiftIndicesProfile.profile(k < n1); k++) {
                    indices[k] = indices[k + 1];
                }
                indices[n1] = num;
                i = i - 1;
            }

            self.setStopped(true);
            if (isStartedProfile.profile(self.isStarted())) {
                throw raise(StopIteration);
            } else {
                self.setStarted(true);
            }
            return factory().createTuple(result);
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isRaisedStopIteration()")
        Object reduce(PPermutations self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            PTuple tuple = factory().createTuple(new Object[]{self.getPool(), self.getR()});

            // we must pickle the indices and use them for setstate
            PTuple indicesTuple = factory().createTuple(self.getIndices());
            PTuple cyclesTuple = factory().createTuple(self.getCycles());
            PTuple tuple2 = factory().createTuple(new Object[]{indicesTuple, cyclesTuple, self.isStarted()});

            Object[] result = new Object[]{type, tuple, tuple2};
            return factory().createTuple(result);
        }

        @Specialization(guards = "self.isRaisedStopIteration()")
        Object reduceStopped(PPermutations self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            PTuple tuple = factory().createTuple(new Object[]{factory().createEmptyTuple(), self.getR()});
            Object[] result = new Object[]{type, tuple};
            return factory().createTuple(result);
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        abstract Object execute(VirtualFrame frame, PythonObject self, Object state);

        @Specialization
        Object setState(VirtualFrame frame, PPermutations self, Object state,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached GetItemNode getItemNode,
                        @Cached LoopConditionProfile indicesProfile,
                        @Cached LoopConditionProfile cyclesProfile,
                        @Cached BranchProfile wrongStateSizeProfile,
                        @Cached BranchProfile wrongValuesSizeProfile) {
            if (sizeNode.execute(frame, state) != 3) {
                wrongStateSizeProfile.enter();
                throw raise(ValueError, INVALID_ARGS, __SETSTATE__);
            }
            Object indices = getItemNode.execute(frame, state, 0);
            Object cycles = getItemNode.execute(frame, state, 1);
            int poolLen = sizeNode.execute(frame, self.getPool());
            if (sizeNode.execute(frame, indices) != poolLen || sizeNode.execute(frame, cycles) != self.getR()) {
                wrongValuesSizeProfile.enter();
                throw raise(ValueError, INVALID_ARGS, __SETSTATE__);
            }

            self.setStarted((boolean) getItemNode.execute(frame, state, 2));
            indicesProfile.profileCounted(poolLen);
            for (int i = 0; indicesProfile.inject(i < poolLen); i++) {
                int index = (int) getItemNode.execute(frame, indices, i);
                if (index < 0) {
                    index = 0;
                } else if (index > poolLen - 1) {
                    index = poolLen - 1;
                }
                self.getIndices()[i] = index;
            }

            cyclesProfile.profileCounted(self.getR());
            for (int i = 0; cyclesProfile.inject(i < self.getR()); i++) {
                int index = (int) getItemNode.execute(frame, cycles, i);
                if (index < 1) {
                    index = 1;
                } else if (index > poolLen - i) {
                    index = poolLen - 1;
                }
                self.getCycles()[i] = index;
            }

            return PNone.NONE;
        }
    }

}
