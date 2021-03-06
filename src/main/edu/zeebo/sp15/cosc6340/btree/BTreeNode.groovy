package edu.zeebo.sp15.cosc6340.btree

/**
 * User: Eric
 * Date: 4/30/2015
 */
class BTreeNode<K> {

	static final enum Type {
		POINTER, LEAF, BUCKET
	}

	static final int MAX_KEYS = 10
	static final int MAX_ENTRIES = 4

	// Create a new internal node: BTreeNode.node
	static BTreeNode<K> getPointer(BTreeNode<K> node = new BTreeNode<>()) { node.type = Type.POINTER; node.size = MAX_KEYS + 1; node }
	// Create a new leaf node: BTreeNode.leaf
	static BTreeNode<K> getLeaf(BTreeNode<K> node = new BTreeNode<>()) { node.type = Type.LEAF; node.size = MAX_KEYS; node }
	// Create a new bucket node: BTreeNode.bucket
	static BTreeNode<K> getBucket(BTreeNode<K> node = new BTreeNode<>()) { node.type = Type.BUCKET; node.size = MAX_ENTRIES; node }

	boolean isLeafNode() {
		return type == Type.LEAF
	}
	boolean isBucketNode() {
		return type == Type.BUCKET
	}
	boolean isInternalNode() {
		return type == Type.POINTER
	}

	Type type
	int size

	def pointers = new LinkedList<BTreeEntry>()
	// The 11th pointer
	BTreeNode<K> leftSibling
	BTreeNode<K> rightSibling

	BTreeNode<K> parent

	// create a new node of the same type, with no pointers assigned
	BTreeNode<K> clone() {
		"get${type.name().toLowerCase().capitalize()}"()
	}

	def getCount() { pointers.size() }

	def getSize() {
		if (bucketNode) {
			return count
		}
		pointers*.value.sum { it.size }
	}

	def getSmallestKey() {
		bucketNode ? pointers[0].key : pointers[0].value.smallestKey
	}

	def getPointerIndex(K key) {
		pointers.findIndexOf { it.key == null || it.key > key }
	}

	def split() {
		// Create sibling node
		BTreeNode<K> sib = clone()

		// Assign the parent
		sib.parent = parent

		// Split our pointer set
		sib.pointers = pointers.subList(0, count / 2 as int) as LinkedList
		pointers = pointers.subList(count / 2 as int, count) as LinkedList

		// Reassign the parent and clear the last key
		if (!bucketNode) {
			sib.pointers*.value*.parent = sib
			if (internalNode) {
				sib.pointers[-1].key = null
			}
		} else {
			parent.pointers.find { it.value == this }.key = smallestKey
		}

		// Reassign the sibling hierarchy
		leftSibling?.rightSibling = sib
		sib.leftSibling = leftSibling
		sib.rightSibling = this
		leftSibling = sib

		// Add sibling to our parent
		parent.addDirect(new BTreeEntry(bucketNode ? sib.smallestKey : this.smallestKey, sib))
	}

	def addDirect(BTreeEntry entry) {

		if (entry.key == null) {
			pointers.add(entry)
		}
		else {
			int idx = getPointerIndex entry.key
			idx >= 0 ? pointers.add(idx, entry) : pointers << entry

			// if we assign a new lowest key
			if (parent != null && idx == 0) {
				if ((idx = parent.pointers.findIndexOf { it.value == this } - 1) >= 0) {
					parent.pointers[idx].key = this.smallestKey
				}
			}
		}

		if (count > size) {
			split()
		}
	}

	def add(K key, value) {
		if (bucketNode) {
			addDirect(new BTreeEntry(key, value))
		}
		else {
			int idx

			if (leafNode) {
				idx = pointers.findLastIndexOf { it.key < key }
				// Possibly insert a new bucket
				if (idx == -1) {
					pointers.add 0, new BTreeEntry(key, bucket)
					pointers[0].value.parent = this
					idx = 0
				}
			}
			else {
				idx = pointers.findIndexOf { it.key >= key }
			}

			(pointers[idx].value as BTreeNode).add key, value
		}
	}

	def search(K key) {
		// bucket
		bucketNode ? pointers.find { it.key == key }?.value :
				// leaf
				leafNode ? pointers[pointers.findLastIndexOf { it.key <= key }].value.search(key) :
						// internal
						pointers[getPointerIndex(key)].value.search(key)
	}

	def deleteDirect(K key) {
		int idx = pointers.findIndexOf { it.key == key }

		// Delete the block element
		if (bucketNode) {
			pointers[idx].value.delete()
		} else {
			BTreeNode node = pointers[idx].value
			node.leftSibling?.rightSibling = node.rightSibling
			node.rightSibling?.leftSibling = node.leftSibling
		}

		// Actually remove the pointer
		pointers.remove(idx)

		if (parent != null) {
			// Remove this node from the parent
			if (count == 0) {
				parent.deleteDirect(parent.pointers.find { it.value == this }.key)
			} else {
				// Reassign the previous pointer
				if (idx == 0) {
					if ((idx = parent.pointers.findIndexOf { it.value == this } - (bucketNode ? 0 : 1)) >= 0) {
						parent.pointers[idx].key = this.smallestKey
					}
				}
			}
		}
	}

	def delete(K key) {
		if (bucketNode) {
			deleteDirect(key)
		}
		else {
			int idx = leafNode ? pointers.findLastIndexOf { it.key <= key } : getPointerIndex(key)
			(pointers[idx].value as BTreeNode).delete(key)
		}
	}

	def list() {
		if (leafNode) {
			def elements = []
			BTreeNode node = this

			while (node != null) {
				node.pointers.each {
					elements << it.value.pointers*.key
				}
				node = node.rightSibling
			}
			return elements.flatten()
		}
		else {
			pointers[0].value.list()
		}
	}

	def getPointersString(int indent = 0) {

		String output = ""
		output +=  '\t' * indent
		if (count > 0) {
			output += "[${pointers[0]?.key}, ${pointers.reverse(false).find { it.key != null }?.key}]"
		}
		output += '\n'

		if (!leafNode) {
			output += pointers*.value*.getPointersString(indent + 1).join()
		}
		return output
	}

	def printTree(int indent = 0) {

		String output = ""
		output +=  '\t' * indent
		if (count > 0) {
			output += pointers.collect { "${it.key}(${it.value.hashCode()})" }.join(' ')
		}
		output += '\n'

		if (!bucketNode) {
			output += pointers*.value*.printTree(indent + 1).join()
		}
		return output
	}

	def getDepth() {
		if (bucketNode) {
			return 0
		}
		return 1 + (pointers*.value*.depth.max() as int)
	}
}
