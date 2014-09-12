/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.k2js.inline.util

import com.google.dart.compiler.backend.js.ast.JsExpression
import com.google.dart.compiler.backend.js.ast.RecursiveJsVisitor
import com.google.dart.compiler.backend.js.ast.JsNode
import com.google.dart.compiler.backend.js.ast.JsBinaryOperation
import com.google.dart.compiler.backend.js.ast.JsPostfixOperation
import com.google.dart.compiler.backend.js.ast.JsPrefixOperation
import com.google.dart.compiler.backend.js.ast.JsNew
import com.google.dart.compiler.backend.js.ast.JsInvocation

public fun canHaveSideEffect(expression: JsExpression): Boolean {
    val visitor = SideEffectExpessionVisitor()
    visitor.accept(expression)
    return visitor.canHaveSideEffect
}

private class SideEffectExpessionVisitor() : RecursiveJsVisitor() {
    public var canHaveSideEffect: Boolean = false
        private set

    override fun visitElement(node: JsNode?) {
        if (!canHaveSideEffect) {
            super<RecursiveJsVisitor>.visitElement(node)
        }
    }

    override fun visitBinaryExpression(x: JsBinaryOperation?) {
        canHaveSideEffect = true
    }

    override fun visitPostfixOperation(x: JsPostfixOperation?) {
        canHaveSideEffect = true
    }

    override fun visitPrefixOperation(x: JsPrefixOperation?) {
        canHaveSideEffect = true
    }

    override fun visitNew(x: JsNew?) {
        canHaveSideEffect = true
    }

    override fun visitInvocation(invocation: JsInvocation?) {
        canHaveSideEffect = true
    }
}