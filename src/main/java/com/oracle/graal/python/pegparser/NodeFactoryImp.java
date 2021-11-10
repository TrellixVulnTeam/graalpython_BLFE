/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

// TODO this class has to be moved to impl package and from this package we need to do api.

import com.oracle.graal.python.pegparser.sst.AnnAssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.AnnotationSSTNode;
import com.oracle.graal.python.pegparser.sst.AssignmentSSTNode;
import com.oracle.graal.python.pegparser.sst.BinaryArithmeticSSTNode;
import com.oracle.graal.python.pegparser.sst.BlockSSTNode;
import com.oracle.graal.python.pegparser.sst.BooleanLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StringLiteralSSTNode.RawStringLiteralSSTNode;
import com.oracle.graal.python.pegparser.sst.UnarySSTNode;
import com.oracle.graal.python.pegparser.sst.VarLookupSSTNode;


public class NodeFactoryImp implements NodeFactory{

    @Override
    public AnnAssignmentSSTNode createAnnAssignment(AnnotationSSTNode annotation, SSTNode rhs, int startOffset, int endOffset) {
        return new AnnAssignmentSSTNode(annotation, rhs, startOffset, endOffset);
    }

    @Override
    public AnnotationSSTNode createAnnotation(SSTNode lhs, SSTNode type, int startOffset, int endOffset) {
        return new AnnotationSSTNode(lhs, type, startOffset, endOffset);
    }

    @Override
    public AssignmentSSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, int startOffset, int endOffset) {
        return new AssignmentSSTNode(lhs, rhs, startOffset, endOffset);
    }
    
    @Override
    public BinaryArithmeticSSTNode createBinaryOp(BinaryArithmeticSSTNode.Type op, SSTNode left, SSTNode right, int startOffset, int endOffset) {
        return new BinaryArithmeticSSTNode(op, left, right, startOffset, endOffset);
    }

    @Override
    public BlockSSTNode createBlock(SSTNode[] statements, int startOffset, int endOffset) {
        return new BlockSSTNode(statements, startOffset, endOffset);
    }
    
    @Override
    public BooleanLiteralSSTNode createBooleanLiteral(boolean value, int startOffset, int endOffset) {
        return new BooleanLiteralSSTNode(value, startOffset, endOffset);
    }

    @Override
    public SSTNode createNumber(String number, int startOffset, int endOffset) {
        // TODO handle all kind of numbers here.
        return NumberLiteralSSTNode.create(number, 0, 10, startOffset, endOffset);
    }

    @Override
    public SSTNode createString(String str, int startOffset, int endOffset) {
        // TODO...
        return new RawStringLiteralSSTNode(str, startOffset, endOffset);
    }

    @Override
    public UnarySSTNode createUnaryOp(UnarySSTNode.Type op, SSTNode value, int startOffset, int endOffset) {
        return new UnarySSTNode(op, value, startOffset, endOffset);
    }
    
    @Override
    public VarLookupSSTNode createVariable(String name, int startOffset, int endOffset) {
        return new VarLookupSSTNode(name, startOffset, endOffset);
    }

}
