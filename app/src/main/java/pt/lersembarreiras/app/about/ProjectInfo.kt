package pt.lersembarreiras.app.about

data class Supporter(
    val id: String,
    val name: String,
    val supportType: String,
    val logoAsset: String? = null,
    val url: String? = null,
    val approvedBySchool: Boolean = false,
)

object ProjectInfo {
    const val NAME = "Ler sem Barreiras"
    const val TAGLINE = "Textos e narração sincronizados, no próprio tablet, sem internet."
    const val MISSION =
        "Apoiar alunos com dificuldades de leitura e comunicação, e os professores que os acompanham, com leitura guiada palavra a palavra."
    const val SCHOOL = "Agrupamento de Escolas Gil Vicente"
    const val SCHOOL_SHORT = "Escola Gil Vicente"
    const val SCHOOL_ROLE = "Entidade promotora e responsável pela iniciativa pedagógica."
    const val AUDIENCE =
        "Alunos com dificuldades de leitura e comunicação, e os professores que preparam os conteúdos no próprio tablet."
    const val HOW_IT_WORKS =
        "O professor escreve o texto, grava ou importa a narração e sincroniza as palavras com o áudio. O aluno ouve a voz original e vê a palavra destacada. Tudo corre no dispositivo, sem conta e sem internet."
    const val TECH_PARTNER = "HXB Informática e Tecnologia, Unipessoal Lda."
    const val TECH_ROLE =
        "Parceiro tecnológico responsável pelo desenvolvimento voluntário e sem custo para a escola. A HXB não reivindica titularidade exclusiva do projeto nem dos conteúdos pedagógicos."
    const val COPYRIGHT_NOTE =
        "Os direitos de autor sobre o código pertencem aos respetivos autores e colaboradores, nos termos da GNU GPL v3.0. A responsabilidade pedagógica é da escola; o desenvolvimento técnico é da HXB. Não se presume cessão de direitos ainda não formalizada."
    const val LICENSE = "GNU GPL v3.0"
    const val GITHUB_URL = "https://github.com/HXB-Informatica/ler-sem-barreiras"

    val supporters: List<Supporter> = emptyList()

    fun approvedSupporters(): List<Supporter> =
        supporters.filter { it.approvedBySchool && it.name.isNotBlank() }
}
