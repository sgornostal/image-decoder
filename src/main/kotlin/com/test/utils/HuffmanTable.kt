package com.test.utils

/**
 * @author Anton Kurinnoy
 */
class HuffmanTable(private val lookup: HashMap<Int, IntArray>) {
    private var root: Node? = null

    private class Node() {
        // node in binary tree
        var symbol: Int
        lateinit var children: Array<Node> // children[0] - left child, children[1] right child

        var parent: Node? = null

        init { // root
            symbol = -1 // nodes left with symbol -1 are not leaf nodes, i.e have children
        }

        private constructor(parent: Node) : this() {
            this.parent = parent
        }

        fun initChildNodes() {
            children = arrayOf(Node(this), Node(this))
        }
    }

    init {
        // hashmap reference to code lengths with corresponding symbols

        // construct huffman tree
        root = Node()
        root!!.initChildNodes()
        var leftMost: Node = root!!.children[0]
        var current: Node?
        for (i in 1..lookup.size) {
            if (getSymbolCount(i) == 0) {
                current = leftMost
                while (current != null) {
                    current.initChildNodes()
                    current = getRightNodeOf(current)
                }
                leftMost = leftMost.children[0]
            } else { // symbols to put into the nodes of the binary tree
                for (symbol in getSymbols(i)!!) {
                    leftMost.symbol = symbol
                    leftMost = getRightNodeOf(leftMost)!!
                }
                leftMost.initChildNodes()
                current = getRightNodeOf(leftMost)
                leftMost = leftMost.children[0]
                while (current != null) {
                    current.initChildNodes()
                    current = getRightNodeOf(current)
                }
            }
        }
    }

    private fun getSymbolCount(n: Int): Int { // # of symbols with length n bits
        return lookup[n]!!.size
    }

    private fun getSymbols(n: Int): IntArray? { // returns list of symbols with length n bits
        return lookup[n]
    }

    private fun getRightNodeOf(node: Node): Node? {
        var node: Node = node
        if (node.parent!!.children[0] === node) return node.parent!!.children[1]
        var traverseCount = 0
        while (node.parent != null && node.parent!!.children[1] === node) {
            node = node.parent!!
            traverseCount++
        }
        if (node.parent == null) return null
        node = node.parent!!.children[1]
        while (traverseCount > 0) {
            node = node.children[0]
            traverseCount--
        }
        return node
    }

    fun getCode(stream: BitStream): Int {
        var currentNode: Node? = root
        while (currentNode!!.symbol == -1) {
            val bit: Int = stream.bit()
            if (bit < 0) { // end of bit stream
                return bit // no more codes to read
            }
            currentNode = currentNode.children[bit]
        }
        return currentNode.symbol
    }
}