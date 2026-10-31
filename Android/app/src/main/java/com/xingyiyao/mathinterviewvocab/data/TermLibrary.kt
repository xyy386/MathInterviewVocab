package com.xingyiyao.mathinterviewvocab.data

import android.content.res.AssetManager
import com.xingyiyao.mathinterviewvocab.core.Term
import com.xingyiyao.mathinterviewvocab.core.VocabularyCore
import com.xingyiyao.mathinterviewvocab.core.VocabularyJson

object TermLibrary {
    fun load(assets: AssetManager): List<Term> = assets.open("terms.json").bufferedReader(Charsets.UTF_8).use {
        decodeAndValidate(it.readText())
    }

    /** 稳定编号是学习记录的外键，不能仅按总条数判断词库完整。 */
    fun decodeAndValidate(source: String): List<Term> {
        val terms = VocabularyJson.decodeTerms(source)
        require(terms.size == 326 && terms.count { it.important } == 130) { "词库条目或重点数量不完整。" }
        require(terms.map { it.id }.toSet() == (1..326).toSet()) { "词库编号不完整或重复。" }
        val counts = terms.groupingBy { it.subjectID }.eachCount()
        require(counts == expectedCounts) { "词库科目或条目数量不完整。" }
        require(terms.all {
            listOf(it.chinese, it.english, it.definition, it.speechText,
                it.subjectName, it.subjectNameEn, it.pronunciation.americanIPA,
                it.pronunciation.britishIPA).all(String::isNotBlank)
        }) { "词库内容或音标缺失。" }
        require(VocabularyCore.subjectOrder.all { id ->
            terms.filter { it.subjectID == id }.map { it.subjectName to it.subjectNameEn }.distinct().size == 1
        }) { "词库科目信息不一致。" }
        return terms
    }

    private val expectedCounts = mapOf(
        "subjects" to 11, "analysis" to 163, "algebra" to 110,
        "probability" to 27, "statistics" to 15,
    )
}
