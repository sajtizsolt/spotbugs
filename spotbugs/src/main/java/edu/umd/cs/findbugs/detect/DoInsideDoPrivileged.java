/*
 * FindBugs - Find Bugs in Java programs
 * Copyright (C) 2005, University of Maryland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.bytecode.MemberUtils;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import edu.umd.cs.findbugs.BugAccumulator;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ch.Subtypes2;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;

/**
 * @author pugh
 */
public class DoInsideDoPrivileged extends BytecodeScanningDetector {
    BugAccumulator bugAccumulator;

    public DoInsideDoPrivileged(BugReporter bugReporter) {
        this.bugAccumulator = new BugAccumulator(bugReporter);
    }

    private boolean isDoPrivileged = false;
    private boolean isDoPrivilegedDeprecated = false;

    @Override
    public void visit(JavaClass obj) {
        isDoPrivilegedDeprecated = obj.getMajor() >= Const.MAJOR_17;

        isDoPrivileged = Subtypes2.instanceOf(getDottedClassName(), "java.security.PrivilegedAction")
                || Subtypes2.instanceOf(getDottedClassName(), "java.security.PrivilegedExceptionAction");
    }

    @Override
    public void visit(Code obj) {
        if (isDoPrivilegedDeprecated) {
            return;
        }
        if (isDoPrivileged && "run".equals(getMethodName())) {
            return;
        }
        if (getMethod().isPrivate()) {
            return;
        }
        if (DumbMethods.isTestMethod(getMethod())) {
            return;
        }
        super.visit(obj);
        bugAccumulator.reportAccumulatedBugs();
    }

    @Override
    public void sawOpcode(int seen) {
        if (seen == Const.INVOKEVIRTUAL && "setAccessible".equals(getNameConstantOperand())) {
            @DottedClassName
            String className = getDottedClassConstantOperand();
            if ("java.lang.reflect.Field".equals(className) || "java.lang.reflect.Method".equals(className)) {
                bugAccumulator.accumulateBug(
                        new BugInstance(this, "DP_DO_INSIDE_DO_PRIVILEGED", LOW_PRIORITY).addClassAndMethod(this)
                                .addCalledMethod(this), this);
            }

        }
        if (seen == Const.NEW) {
            @DottedClassName
            String classOfConstructedClass = getDottedClassConstantOperand();
            if (Subtypes2.instanceOf(classOfConstructedClass, "java.lang.ClassLoader")
                    && !MemberUtils.isMainMethod(getMethod())) {
                bugAccumulator.accumulateBug(new BugInstance(this, "DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED", NORMAL_PRIORITY)
                        .addClassAndMethod(this).addClass(classOfConstructedClass), this);
            }
        }

    }

}
