/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.components

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.idea.fir.low.level.api.api.getOrBuildFir
import org.jetbrains.kotlin.idea.fir.low.level.api.api.getOrBuildFirOfType
import org.jetbrains.kotlin.idea.fir.low.level.api.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.idea.frontend.api.components.KtPsiTypeProvider
import org.jetbrains.kotlin.idea.frontend.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.fir.utils.getReferencedElementType
import org.jetbrains.kotlin.idea.frontend.api.tokens.ValidityToken
import org.jetbrains.kotlin.idea.frontend.api.withValidityAssertion
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDoubleColonExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeReference

internal class KtFirPsiTypeProvider(
    override val analysisSession: KtFirAnalysisSession,
    override val token: ValidityToken,
) : KtPsiTypeProvider(), KtFirAnalysisSessionComponent {
    override fun getPsiTypeForKtExpression(
        expression: KtExpression,
        mode: TypeMappingMode,
    ): PsiType = withValidityAssertion {
        when (val fir = expression.getOrBuildFir(firResolveState)) {
            is FirExpression -> fir.typeRef.coneType.asPsiType(mode, expression)
            is FirNamedReference -> fir.getReferencedElementType().asPsiType(mode, expression)
            is FirStatement -> PsiType.VOID
            else -> throwUnexpectedFirElementError(fir, expression)
        }
    }

    override fun getPsiTypeForKtDeclaration(
        ktDeclaration: KtDeclaration,
        mode: TypeMappingMode
    ): PsiType = withValidityAssertion {
        val firDeclaration = ktDeclaration.getOrBuildFirOfType<FirCallableDeclaration>(firResolveState)
        firDeclaration.returnTypeRef.coneType.asPsiType(mode, ktDeclaration)
    }

    override fun getPsiTypeForKtTypeReference(
        ktTypeReference: KtTypeReference,
        mode: TypeMappingMode
    ): PsiType = withValidityAssertion {
        when (val fir = ktTypeReference.getOrBuildFir(firResolveState)) {
            // NB: [FirErrorTypeRef] is a subtype of [FirResolvedTypeRef], and the error type in it will be properly handled by [asPsiType].
            is FirResolvedTypeRef -> fir.coneType.asPsiType(mode, ktTypeReference)
            else -> throwUnexpectedFirElementError(fir, ktTypeReference)
        }
    }

    override fun getPsiTypeForReceiverOfDoubleColonExpression(
        ktDoubleColonExpression: KtDoubleColonExpression,
        mode: TypeMappingMode
    ): PsiType? = withValidityAssertion {
        val receiver = ktDoubleColonExpression.receiverExpression ?: return null
        when (val fir = ktDoubleColonExpression.getOrBuildFir(firResolveState)) {
            is FirGetClassCall ->
                fir.typeRef.coneType.getReceiverOfReflectionType()?.asPsiType(mode, receiver)
            is FirCallableReferenceAccess ->
                fir.typeRef.coneType.getReceiverOfReflectionType()?.asPsiType(mode, receiver)
            else -> throwUnexpectedFirElementError(fir, ktDoubleColonExpression)
        }
    }

    private fun ConeKotlinType.getReceiverOfReflectionType(): ConeKotlinType? {
        if (this !is ConeClassLikeType) return null
        if (lookupTag.classId.packageFqName != StandardClassIds.BASE_REFLECT_PACKAGE) return null
        return typeArguments.firstOrNull()?.type
    }
}
