/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.plugins.hcl.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.folding.NamedFoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.intellij.plugins.hcl.HCLElementTypes
import org.intellij.plugins.hcl.psi.HCLArray
import org.intellij.plugins.hcl.psi.HCLObject
import org.intellij.plugins.hcl.psi.getNextSiblingNonWhiteSpace

class HCLFoldingBuilder : CustomFoldingBuilder(), DumbAware {
  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    return false
  }

  override fun buildLanguageFoldRegions(descriptors: MutableList<FoldingDescriptor>, root: PsiElement, document: Document, quick: Boolean) {
    collect(root, descriptors, HashSet())
  }

  private fun collect(element: PsiElement, descriptors: MutableList<FoldingDescriptor>, usedComments: MutableSet<PsiElement>) {
    val node = element.node
    when (node.elementType) {
      HCLElementTypes.OBJECT -> {
        if (isSpanMultipleLines(node) && element is HCLObject) {
          val props = element.propertyList.size
          val blocks = element.blockList.size
          val count = props + blocks
          when (count) {
            0, 1 -> descriptors.add(NamedFoldingDescriptor(node, node.textRange, null, getCollapsedObjectPlaceholder(element)))
            else -> descriptors.add(FoldingDescriptor(node, node.textRange))
          }
        }
      }
      HCLElementTypes.ARRAY -> {
        if (isSpanMultipleLines(node) && element is HCLArray) {
          val count = element.valueList.size
          when (count) {
            0, 1 -> descriptors.add(NamedFoldingDescriptor(node, node.textRange, null, getCollapsedArrayPlaceholder(element)))
            else -> descriptors.add(FoldingDescriptor(node, node.textRange))
          }
        }
      }
      HCLElementTypes.BLOCK_COMMENT -> descriptors.add(FoldingDescriptor(node, node.textRange))
      HCLElementTypes.LINE_COMMENT -> {
        if (usedComments.add(element)) {
          if (!isCustomRegionElement(element)) {
            var end: PsiElement? = null
            var current: PsiElement? = element.getNextSiblingNonWhiteSpace()
            while (current != null) {
              if (current.node.elementType === HCLElementTypes.LINE_COMMENT) {
                if (isCustomRegionElement(current)) {
                  // Stop current folding
                  usedComments.add(current)
                  break
                }
                end = current
                usedComments.add(current)
                current = current.getNextSiblingNonWhiteSpace()
                continue
              }
              break
            }
            if (end != null) {
              val range = TextRange(element.textRange.startOffset, end.textRange.endOffset)
              descriptors.add(FoldingDescriptor(element, range))
            }
          }
        }
      }
    }

    val childUsedCommends = HashSet<PsiElement>()
    for (c in element.children) {
      collect(c, descriptors, childUsedCommends)
    }
  }

  private fun getCollapsedObjectPlaceholder(element: HCLObject, limit: Int = 30): String {
    val props = element.propertyList.size
    val blocks = element.blockList.size
    if (props + blocks == 0) return "{}"
    else if (props + blocks != 1) return "{...}"

    val prop = element.propertyList.firstOrNull()
    if (prop != null) {
      if (prop.textLength > limit) return "{...}"
      return "{" + prop.text + "}"
    }
    val bl = element.blockList.firstOrNull()
    if (bl != null) {
      if (bl.name.length > limit) return "{...}"
      val obj = bl.`object` ?: return "{...}"
      val inner = getCollapsedObjectPlaceholder(obj, limit - (bl.name.length + 3))
      return "{${bl.name} $inner}"
    }
    return "{}"
  }

  private fun getCollapsedArrayPlaceholder(element: HCLArray, limit: Int = 30): String {
    val vals = element.valueList
    if (vals.isEmpty()) return "[]"
    if (vals.size > 1) return "[...]"
    val node = vals.first().node
    if (node.textLength > limit) return "[...]"
    return "[${node.text}]"
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    return when (node.elementType) {
      HCLElementTypes.ARRAY -> "[...]"
      HCLElementTypes.OBJECT -> "{...}"
      HCLElementTypes.BLOCK_COMMENT -> "/*...*/"
      HCLElementTypes.LINE_COMMENT -> "//..."
      else -> "..."
    }
  }

  private fun isSpanMultipleLines(node: ASTNode): Boolean {
    return node.textContains('\n') || node.textContains('\r')
  }
}
