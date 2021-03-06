/*
 * Copyright (C) 2017-2018 Dremio Corporation
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
package com.dremio.sabot.op.llvm;

import java.util.ArrayList;
import java.util.List;

import com.dremio.sabot.exec.context.OperatorStats;
import org.apache.arrow.gandiva.exceptions.GandivaException;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.types.pojo.Schema;

import com.dremio.common.expression.LogicalExpression;
import com.dremio.exec.record.VectorAccessible;

/**
 * Used to construct a native projector for a set of expressions.
 */
public class NativeProjectorBuilder {

  private final NativeProjectEvaluator NO_OP = new NoOpNativeProjectEvaluator();

  private List<ExprPairing> exprs = new ArrayList<>();
  private List<ValueVector> allocationVectors = new ArrayList<>();
  private final VectorAccessible incoming;

  public NativeProjectorBuilder(VectorAccessible incoming) {
    this.incoming = incoming;
  }

  /**
   * Add the provided expression to the native evaluator.
   * @param expr The expression to be written into the output vector.
   * @param outputVector the vector to write to.
   * @return true if expression can be handled natively. false if the expression cannot be handled natively.
   */
  public void add(LogicalExpression expr, ValueVector outputVector) throws GandivaException {
    ExprPairing pairing = new ExprPairing(expr, (FieldVector) outputVector);
    exprs.add(pairing);
  }

  public NativeProjectEvaluator build(Schema incomingSchema, OperatorStats stats) throws Exception {
    if(exprs.isEmpty()) {
      return NO_OP;
    }

    final NativeProjector projector = new NativeProjector(incoming, incomingSchema);
    for (ExprPairing e : exprs) {
      projector.add(e.expr, e.outputVector);
      allocationVectors.add(e.outputVector);
    }
    projector.build();

    return new NativeProjectEvaluator() {

      @Override
      public void close() throws Exception {
        projector.close();
      }

      @Override
      public void evaluate(int recordCount) throws Exception{
        projector.execute(recordCount, allocationVectors);
      }};

  }

  private static class ExprPairing {
    private final LogicalExpression expr;
    private final FieldVector outputVector;
    public ExprPairing(LogicalExpression expr, FieldVector outputVector) {
      super();
      this.expr = expr;
      this.outputVector = outputVector;
    }

  }

  private static class NoOpNativeProjectEvaluator extends NativeProjectEvaluator {

    @Override
    public void evaluate(int recordCount) {
    }

    @Override
    public void close() {
    }

  }
}
