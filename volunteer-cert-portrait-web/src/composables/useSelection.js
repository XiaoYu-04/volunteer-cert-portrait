import { computed, ref } from 'vue'

/**
 * 列表页「全选本页 / 逐条勾选」的选中集合。
 *
 *   const { selected, candidates: selectable, allChecked, toggleAll, toggleOne } = useSelection(
 *     rows,
 *     { selectable: (r) => r.status === 'PENDING' },
 *   )
 *
 * rows 为当前页数据（Ref），selectable 判定本页哪些行可勾选；返回的 candidates 即页面上
 * 用来显示「全选本页 N 条」的那个集合。选中集合存行 id，翻页不自动清空（与各列表页原实现
 * 一致），改完筛选条件后可调用 clear()。
 *
 * 选中集合默认在此新建；若它必须落在别处（如弹窗对象的 dialog.selected），传入对应的
 * ref / 可写 computed 即可，勾选行为不变。
 */
export function useSelection(rows, { selectable = () => true, selected = ref([]) } = {}) {
  const candidates = computed(() => rows.value.filter(selectable))

  const allChecked = computed(
    () => candidates.value.length > 0 && selected.value.length === candidates.value.length,
  )

  function toggleAll(e) {
    selected.value = e.target.checked ? candidates.value.map((r) => r.id) : []
  }

  function toggleOne(id, checked) {
    if (checked) {
      if (!selected.value.includes(id)) selected.value.push(id)
    } else {
      selected.value = selected.value.filter((x) => x !== id)
    }
  }

  function clear() {
    selected.value = []
  }

  return { selected, candidates, allChecked, toggleAll, toggleOne, clear }
}
