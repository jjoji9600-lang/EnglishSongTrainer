package com.englishsongtrainer

data class Cue(val start: Long, val end: Long?, val text: String)
object Lyrics {
    private val lrc = Regex("""\[(\d+):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val off = Regex("""\[offset:\s*([+-]?\d+)\]""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private fun frac(s: String) = s.padEnd(3,'0').take(3).toLongOrNull() ?: 0L
    private fun time(s: String): Long? {
        val m = clock.matchEntire(s.trim()) ?: return null
        return (m.groupValues[1].toLongOrNull() ?: 0L)*3600000 +
            m.groupValues[2].toLong()*60000 + m.groupValues[3].toLong()*1000 + frac(m.groupValues[4])
    }
    fun parse(raw: String): List<Cue> {
        val src=raw.removePrefix("\uFEFF").replace("\r\n","\n").replace('\r','\n')
        if(src.contains("-->")) {
            val rows=src.lines()
            val out=mutableListOf<Cue>()
            var i=0
            while(i<rows.size) {
                val row=rows[i++]
                if(!row.contains("-->")) continue
                val parts=row.split("-->",limit=2)
                val start=time(parts[0]) ?: continue
                val end=time(parts[1].trim().substringBefore(' ')) ?: continue
                val body=mutableListOf<String>()
                while(i<rows.size && rows[i].isNotBlank() && !rows[i].contains("-->")) body.add(rows[i++].trim())
                val text=body.joinToString("\n").replace(Regex("<[^>]+>"),"").trim()
                if(end>start && text.isNotEmpty()) out.add(Cue(start,end,text))
            }
            return out.sortedBy { it.start }
        }
        val shift=off.find(src)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return src.lines().flatMap { row ->
            val text=lrc.replace(row,"").trim()
            if(text.isEmpty()) emptyList() else lrc.findAll(row).map { m ->
                Cue((m.groupValues[1].toLong()*60000+m.groupValues[2].toLong()*1000+
                    frac(m.groupValues[3])+shift).coerceAtLeast(0),null,text)
            }.toList()
        }.sortedBy { it.start }.groupBy { it.start }.map { (start,cs) ->
            Cue(start,null,cs.joinToString("\n"){it.text})
        }
    }
    fun range(cues: List<Cue>, i: Int, shift: Long, duration: Long): Pair<Long,Long> {
        val c=cues[i]
        val start=(c.start+shift).coerceAtLeast(0)
        val rawEnd=c.end ?: cues.getOrNull(i+1)?.start ?: if(duration>0) duration-shift else c.start+5000
        val end=(rawEnd+shift).let{if(duration>0) it.coerceAtMost(duration) else it}
        return start to end
    }
}
