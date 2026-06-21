package com.lightningstudio.watchrss.data.tts

import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudFallbackProgressTest {
    @Test
    fun mixedPhoneModelProgressUsesSpeechUnitsInsteadOfCharacterPosition() {
        val text = "这次 Nothing Phone(1)/(2a)/(3) 都会被提到。"
        val modelStart = text.indexOf("Nothing")
        val modelEnd = text.indexOf(" 都会")
        val unitsBeforeModel = readAloudFallbackSpeechUnits(text.substring(0, modelStart))
        val modelUnits = readAloudFallbackSpeechUnits(text.substring(modelStart, modelEnd))
        val progressInModel = ((unitsBeforeModel + modelUnits * 0.55) /
            readAloudFallbackSpeechUnits(text)).toFloat()

        val offset = readAloudFallbackOffsetForProgress(text, progressInModel)

        assertTrue(offset in modelStart until modelEnd)
    }

    @Test
    fun modelSeparatorsAreSkippedAsFallbackHighlightAnchors() {
        val text = "Nothing Phone(1)/(2a)/(3) 都会被提到。"
        val slash = text.indexOf('/')
        val progressAtSlash = (
            readAloudFallbackSpeechUnits(text.substring(0, slash + 1)) /
                readAloudFallbackSpeechUnits(text)
            ).toFloat()

        val offset = readAloudFallbackReadableOffsetForProgress(text, progressAtSlash)

        assertTrue(text[offset].isLetterOrDigit())
    }

    @Test
    fun sspaiNothingOpeningParagraphKeepsModelProgressInParagraphOrder() {
        val text = "两年前聊 Nothing 品牌处女作 Nothing Phone (1) 的时候就有说到——" +
            "Nothing 凭借中端的 a 系列一飞冲天，而危机恰巧蕴含在靠价格战与差异化的产品策略之中。"
        val phoneModelStart = text.indexOf("Nothing Phone")
        val phoneModelEnd = text.indexOf(" 的时候")
        val unitsBeforeModel = readAloudFallbackSpeechUnits(text.substring(0, phoneModelStart))
        val modelUnits = readAloudFallbackSpeechUnits(text.substring(phoneModelStart, phoneModelEnd))
        val progressInsideModel = ((unitsBeforeModel + modelUnits * 0.65) /
            readAloudFallbackSpeechUnits(text)).toFloat()

        val offset = readAloudFallbackReadableOffsetForProgress(text, progressInsideModel)

        assertTrue(offset in phoneModelStart until phoneModelEnd)
    }

    @Test
    fun latinWordsDoNotCountLikeOneUnitPerCharacter() {
        val units = readAloudFallbackSpeechUnits("Nothing Phone")

        assertTrue(units < 5.0)
        assertTrue(units > 2.0)
    }
}
