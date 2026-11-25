package com.example.devboxai.ai

object Personality {

    data class Template(
        val onWake: String,
        val onListenting: String,
        val success: (String) -> String,
        val error: (String) -> String
    )

    private val templates: Map<String, Template> = mapOf(
        "en" to Template("Yes boss, I'm here!", "Listening boss…",
            { "Boss, ${it} is done!" }, { "Sorry boss, ${it}" }),
        "hi" to Template("जी बॉस, हाज़िर हूँ!", "सुन रहा हूँ बॉस…",
            { "बॉस, ${it} हो गया!" }, { "सॉरी बॉस, ${it}" }),
        "es" to Template("¡Sí jefe, aquí estoy!", "Escuchando jefe…",
            { "¡Jefe, ${it} listo!" }, { "Lo siento jefe, ${it}" }),
        "fr" to Template("Oui chef, je suis là !", "J'écoute chef…",
            { "Chef, ${it} est fait !" }, { "Désolé chef, ${it}" }),
        "de" to Template("Ja Chef, ich bin da!", "Ich höre Chef…",
            { "Chef, ${it} ist erledigt!" }, { "Entschuldigung Chef, ${it}" }),
        "pt" to Template("Sim chefe, estou aqui!", "Ouvindo chefe…",
            { "Chefe, ${it} está pronto!" }, { "Desculpe chefe, ${it}" }),
        "ru" to Template("Да, босс, я здесь!", "Слушаю, босс…",
            { "Босс, ${it} готово!" }, { "Извините, босс, ${it}" }),
        "ar" to Template("نعم يا رئيس، أنا هنا!", "أسمعك يا رئيس…",
            { "يا رئيس، ${it} تم!" }, { "آسف يا رئيس، ${it}" }),
        "zh" to Template("是的老板，我在这！", "老板，我在听…",
            { "老板，${it}完成了！" }, { "抱歉老板，${it}" }),
        "ja" to Template("はいボス、ここにいます！", "ボス、聞いています…",
            { "ボス、${it}完了しました！" }, { "すみませんボス、${it}" }),
        "ko" to Template("네 보스, 여기 있습니다!", "보스, 듣고 있어요…",
            { "보스, ${it} 완료!" }, { "죄송합니다 보스, ${it}" }),
        "it" to Template("Sì capo, sono qui!", "Ascolto capo…",
            { "Capo, ${it} fatto!" }, { "Scusa capo, ${it}" }),
        "tr" to Template("Evet patron, buradayım!", "Dinliyorum patron…",
            { "Patron, ${it} tamam!" }, { "Üzgünüm patron, ${it}" }),
        "bn" to Template("জি বস, হাজির!", "শুনছি বস…",
            { "বস, ${it} হয়ে গেছে!" }, { "দুঃখিত বস, ${it}" }),
        "ta" to Template("ஆமா முதலாளி, நான் இங்கே!", "கேட்கிறேன் முதலாளி…",
            { "முதலாளி, ${it} முடிந்தது!" }, { "மன்னிக்கவும் முதலாளி, ${it}" }),
        "te" to Template("అవును బాస్, నేను ఇక్కడ ఉన్నాను!", "వింటున్నాను బాస్…",
            { "బాస్, ${it} పూర్తయింది!" }, { "క్షమించండి బాస్, ${it}" }),
        "ur" to Template("جی باس، حاضر ہوں!", "سن رہا ہوں باس…",
            { "باس، ${it} ہو گیا!" }, { "معاف کیجیے باس، ${it}" }),
        "id" to Template("Ya bos, saya di sini!", "Mendengarkan bos…",
            { "Bos, ${it} selesai!" }, { "Maaf bos, ${it}" })
    )

    fun forLang(langTag: String): Template {
        val base = LanguageDetector.base(langTag)
        return templates[base] ?: templates["en"]!!
    }

    fun supportedLangs(): List<String> = templates.keys.toList()
}
