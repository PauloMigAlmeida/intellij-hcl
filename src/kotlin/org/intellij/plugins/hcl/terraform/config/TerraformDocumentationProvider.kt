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
package org.intellij.plugins.hcl.terraform.config

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.intellij.plugins.hcl.psi.*
import org.intellij.plugins.hcl.terraform.config.codeinsight.ModelHelper
import org.intellij.plugins.hcl.terraform.config.model.TypeModel

class TerraformDocumentationProvider : AbstractDocumentationProvider() {
  override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
    if (element is HCLProperty) {
      return "Property ${element.name}"
    }
    return null
  }

  override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
    if (element == null) return null
    if (!element.isInHCLFileWithInterpolations()) return null

    if (element is HCLProperty) {
      val pp = element.parent?.parent as? HCLBlock ?: return null
      val properties = ModelHelper.getBlockProperties(pp)
      val property = properties.firstOrNull { it.property?.name == element.name }?.property ?: return "Unknown property ${element.name}"
      return buildString {
        append("Property ")
        append(element.name)
        append(" (")
        append(property.type.name)
        append(")")
        if (property.description != null) {
          append("<br/>")
          append(property.description)
        }
      }
    } else if (element is HCLBlock) {
      val pp = element.parent?.parent
      if (pp !is HCLBlock) {
        val block = TypeModel.RootBlocks.firstOrNull { it.literal == element.getNameElementUnquoted(0) } ?: return null
        if (block == TypeModel.Variable) {
          return buildString {
            append("Variable '")
            append(element.name)
            append('\'')
            (element.`object`?.findProperty(TypeModel.Variable_Type.name)?.value as? HCLStringLiteral)?.value?.let {
              append(" of type ")
              append(it)
            }
            (element.`object`?.findProperty(TypeModel.Variable_Description.name)?.value as? HCLStringLiteral)?.value?.let {
              append("<br/>")
              append(it)
            }
          }
        }
        return buildString {
          append("Block ")
          append(element.name)
          if (block.description != null) {
            append("<br/>")
            append(block.description)
          }
        }
      }
      val properties = ModelHelper.getBlockProperties(pp)
      val block = properties.firstOrNull { it.block?.literal == element.getNameElementUnquoted(0) }?.block ?: return "Unknown block ${element.name}"
      return buildString {
        append("Block ")
        append(element.name)
        if (block.description != null) {
          append("<br/>")
          append(block.description)
        }
      }
    } else if (element is HCLStringLiteral || element is HCLIdentifier) {
      val parent = element.parent
      if (parent is PsiNameIdentifierOwner && parent.nameIdentifier === element) {
        return generateDoc(parent, originalElement)
      }
    }
    return null
  }
}
