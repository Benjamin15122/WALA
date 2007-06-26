/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.core.tests.ir;

import java.util.Iterator;

import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.ClassLoaderFactoryImpl;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.emf.wrappers.EMFScopeWrapper;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.Atom;
import com.ibm.wala.util.ImmutableByteArray;
import com.ibm.wala.util.UTF8Convert;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.warnings.WarningSet;

/**
 * Test that the SSA-numbering of variables in the IR is deterministic.
 * 
 * Introduced 05-AUG-03; the default implementation of hashCode was being
 * invoked. Object.hashCode is a source of random numbers and has no place in a
 * deterministic program.
 */
public class DeterministicIRTest extends WalaTestCase {

  private static final ClassLoader MY_CLASSLOADER = DeterministicIRTest.class.getClassLoader();

  private WarningSet warnings;

  private AnalysisScope scope;

  private ClassHierarchy cha;

  private AnalysisOptions options;

  public static void main(String[] args) {
    justThisTest(DeterministicIRTest.class);
  }

  /*
   * (non-Javadoc)
   * 
   * @see junit.framework.TestCase#setUp()
   */
  protected void setUp() throws Exception {

    warnings = new WarningSet();
    scope = new EMFScopeWrapper(TestConstants.WALA_TESTDATA, "J2SEClassHierarchyExclusions.xml", MY_CLASSLOADER);

    options = new AnalysisOptions(scope, null);
    ClassLoaderFactory factory = new ClassLoaderFactoryImpl(scope.getExclusions(), warnings);

    warnings = new WarningSet();

    try {
      cha = ClassHierarchy.make(scope, factory, warnings);
    } catch (ClassHierarchyException e) {
      throw new Exception();
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see junit.framework.TestCase#tearDown()
   */
  protected void tearDown() throws Exception {
    warnings = null;
    scope = null;
    cha = null;
    super.tearDown();
  }

  /**
   * @param method
   */
  private IR doMethod(MethodReference method) {
    assertNotNull("method not found", method);
    IMethod imethod = cha.resolveMethod(method);
    assertNotNull("imethod not found", imethod);
    IR ir1 = options.getIRFactory().makeIR(imethod, Everywhere.EVERYWHERE, options.getSSAOptions(), warnings);
    options.getSSACache().wipe();

    checkNotAllNull(ir1.getInstructions());
    checkNoneNull(ir1.iterateAllInstructions());
    try {
      GraphIntegrity.check(ir1.getControlFlowGraph());
    } catch (UnsoundGraphException e) {
      System.err.println(ir1);
      e.printStackTrace();
      assertTrue("unsound CFG for ir1", false);
    }

    IR ir2 = options.getIRFactory().makeIR(imethod, Everywhere.EVERYWHERE, options.getSSAOptions(), warnings);
    options.getSSACache().wipe();

    try {
      GraphIntegrity.check(ir2.getControlFlowGraph());
    } catch (UnsoundGraphException e1) {
      System.err.println(ir2);
      e1.printStackTrace();
      assertTrue("unsound CFG for ir2", false);
    }

    assertEquals(ir1.toString(), ir2.toString());
    return ir1;
  }

  // The Tests ///////////////////////////////////////////////////////

  /**
   * @param iterator
   */
  private void checkNoneNull(Iterator<?> iterator) {
    while (iterator.hasNext()) {
      assertTrue(iterator.next() != null);
    }

  }

  /**
   * @param instructions
   */
  private static void checkNotAllNull(SSAInstruction[] instructions) {
    for (int i = 0; i < instructions.length; i++) {
      if (instructions[i] != null) {
        return;
      }
    }
    assertTrue("no instructions generated", false);
  }

  public void testIR1() {
    // 'remove' is a nice short method
    doMethod(scope.findMethod(AnalysisScope.APPLICATION, "Ljava/util/HashMap", Atom.findOrCreateUnicodeAtom("remove"),
        new ImmutableByteArray(UTF8Convert.toUTF8("(Ljava/lang/Object;)Ljava/lang/Object;"))));
  }

  public void testIR2() {
    // 'equals' is a nice medium-sized method
    doMethod(scope.findMethod(AnalysisScope.APPLICATION, "Ljava/lang/String", Atom.findOrCreateUnicodeAtom("equals"),
        new ImmutableByteArray(UTF8Convert.toUTF8("(Ljava/lang/Object;)Z"))));
  }

  public void testIR3() {
    // 'resolveProxyClass' is a nice long method (at least in Sun libs)
    doMethod(scope.findMethod(AnalysisScope.APPLICATION, "Ljava/io/ObjectInputStream", Atom
        .findOrCreateUnicodeAtom("resolveProxyClass"), new ImmutableByteArray(UTF8Convert
        .toUTF8("([Ljava/lang/String;)Ljava/lang/Class;"))));
  }
}
