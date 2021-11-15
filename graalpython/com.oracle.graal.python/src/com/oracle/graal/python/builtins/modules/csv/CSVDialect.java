package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public final class CSVDialect extends PythonBuiltinObject {
    String delimiter;
    boolean doublequote;
    String escapechar;
    String lineterminator;
    int quoting;
    String quotechar;
    boolean skipinitialspace;
    boolean strict;

    public CSVDialect(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public CSVDialect(Object cls, Shape instanceShape, String delimiter, boolean doublequote, String escapechar,
                      String lineterminator, String quotechar, int quoting, boolean skipinitialspace,
                      boolean strict) {
        super(cls, instanceShape);
        this.delimiter = delimiter;
        this.doublequote = doublequote;
        this.escapechar = escapechar;
        this.lineterminator = lineterminator;
        this.quotechar = quotechar;
        this.quoting = quoting;
        this.skipinitialspace = skipinitialspace;
        this.strict = strict;
    }
}
