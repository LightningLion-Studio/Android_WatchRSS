package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** An authenticated JSON document. Unknown values are never parsed and re-encoded for publication. */
class WatchLayout3RawDocument private constructor(val source: String, val root: Node) {
    data class Property(val key: String, val keyStart: Int, val value: Node)
    data class Node(val start: Int, val end: Int, val properties: List<Property>? = null, val elements: List<Node>? = null)
    fun node(path: List<String>): Node? = path.fold(root as Node?) { n,k -> n?.properties?.firstOrNull { it.key==k }?.value }
    fun raw(path: List<String> = emptyList()): String? = node(path)?.let { source.substring(it.start,it.end) }
    fun json(): JSONObject = RawObject(this)
    fun unknownBytes(path: List<String>, known: Set<String>): Int = node(path)?.properties.orEmpty()
        .filter { it.key !in known }.sumOf { source.substring(it.keyStart,it.value.end).toByteArray(Charsets.UTF_8).size }
    fun patch(path: List<String>, token: String): WatchLayout3RawDocument {
        val target=node(path)
        if(target!=null)return parse(source.replaceRange(target.start,target.end,token))
        require(path.isNotEmpty())
        val parent=requireNotNull(node(path.dropLast(1)));require(parent.properties!=null)
        val insertion=(if(parent.properties.isEmpty()) "" else ",")+JSONObject.quote(path.last())+":"+token
        return parse(source.substring(0,parent.end-1)+insertion+source.substring(parent.end-1))
    }
    fun remove(path: List<String>): WatchLayout3RawDocument {
        val parent=node(path.dropLast(1))?:return this
        val properties=parent.properties?:return this
        if(properties.none { it.key==path.last() })return this
        val text=properties.filter { it.key!=path.last() }.joinToString(",","{","}") { source.substring(it.keyStart,it.value.end) }
        return patch(path.dropLast(1),text)
    }
    /** Only supplied fields are patched; absent future properties remain byte-for-byte intact. */
    fun merge(path: List<String>, values: JSONObject): WatchLayout3RawDocument {
        var result=this
        for(key in values.keys()) {
            val next=values.get(key);val child=path+key;val old=result.raw(child)
            if(old!=null && semantic(old)==semantic(encode(next)))continue
            result=if(next is JSONObject && result.node(child)?.properties!=null) result.merge(child,next)
                else result.patch(child,encode(next))
        }
        return result
    }
    companion object {
        fun parse(bytes: ByteArray): WatchLayout3RawDocument = parse(Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString())
        fun parse(source: String): WatchLayout3RawDocument {val p=Parser(source);val n=p.value(0);p.space();require(p.pos==source.length);require(n.properties!=null);return WatchLayout3RawDocument(source,n)}
        fun encode(value: Any?): String = when(value){null,JSONObject.NULL->"null";is JSONObject,is JSONArray->value.toString();is String->JSONObject.quote(value);is Number->JSONObject.numberToString(value);is Boolean->value.toString();else->JSONObject.quote(value.toString())}
        private fun semantic(raw:String):String = runCatching {WatchCloudRecordCodec.canonical(org.json.JSONTokener(raw).nextValue())}.getOrDefault(raw)
    }
    private class RawObject(private var document: WatchLayout3RawDocument?) : JSONObject(requireNotNull(document).source) {
        override fun toString(): String = document?.source ?: super.toString()
        override fun toString(indentSpaces: Int): String = toString()
        override fun put(name: String, value: Any?): JSONObject {
            super.put(name,value)
            document=document?.let {if(value==null)it.remove(listOf(name)) else it.patch(listOf(name),encode(value))}
            return this
        }
        override fun remove(name: String?): Any? {val old=super.remove(name);if(name!=null)document=document?.remove(listOf(name));return old}
    }
    private class Parser(val text: String) {
        var pos=0
        fun space(){while(pos<text.length && text[pos] in " \n\r\t")pos++}
        fun value(depth:Int):Node {
            space();val start=pos;require(pos<text.length)
            return when(text[pos]) {
                '{'->{if(depth>=32)throw WatchCloudUpgradeRequired();pos++;space();val properties=mutableListOf<Property>();val keys=hashSetOf<String>()
                    if(pos<text.length&&text[pos]=='}'){pos++;Node(start,pos,properties)} else {
                        while(true){space();val keyStart=pos;val key=string();require(keys.add(key));space();require(text.getOrNull(pos++)==':');val n=value(depth+1);properties+=Property(key,keyStart,n);space();val c=text.getOrNull(pos++);if(c=='}')break;require(c==',')}
                        Node(start,pos,properties)
                    }}
                '['->{if(depth>=32)throw WatchCloudUpgradeRequired();pos++;space();val values=mutableListOf<Node>();if(text.getOrNull(pos)==']')pos++ else while(true){values+=value(depth+1);space();val c=text.getOrNull(pos++);if(c==']')break;require(c==',')};Node(start,pos,elements=values)}
                '"'->{string();Node(start,pos)}
                else->{while(pos<text.length&&text[pos] !in ",]} \n\r\t")pos++;val token=text.substring(start,pos);require(token in listOf("true","false","null")||token.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")));Node(start,pos)}
            }
        }
        private fun string():String {
            require(text.getOrNull(pos++)=='"');val decoded=StringBuilder()
            while(true){require(pos<text.length);val c=text[pos++];if(c=='"')break;require(c>=' ')
                if(c!='\\')decoded.append(c) else {require(pos<text.length);when(val e=text[pos++]){
                    '"','\\','/'->decoded.append(e);'b'->decoded.append('\b');'f'->decoded.append('\u000c');'n'->decoded.append('\n');'r'->decoded.append('\r');'t'->decoded.append('\t')
                    'u'->{require(pos+4<=text.length);val digits=text.substring(pos,pos+4);require(digits.matches(Regex("[0-9a-fA-F]{4}")));decoded.append(digits.toInt(16).toChar());pos+=4}
                    else->error("invalid JSON escape")}}
            }
            var i=0;while(i<decoded.length){val c=decoded[i++];if(c.isHighSurrogate()){require(i<decoded.length&&decoded[i].isLowSurrogate());i++}else require(!c.isLowSurrogate())}
            return decoded.toString()
        }
    }
}
