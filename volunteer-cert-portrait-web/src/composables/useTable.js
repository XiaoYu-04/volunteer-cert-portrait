import { onMounted, reactive, ref, watch } from 'vue'
import { useToast } from './useToast'

/**
 * 列表页的取数逻辑：分页 + 筛选 + 加载态。
 *
 *   const { rows, total, loading, query, search, load } = useTable(listActivities)
 *
 * 约定接口返回 { total, list }（与架构文档一致）。
 * 翻页由 query.page 的 watch 驱动；改筛选条件后调 search() 回到第一页。
 */
export function useTable(fetcher, { defaultQuery = {}, immediate = true } = {}) {
  const toast = useToast()

  const rows = ref([])
  const total = ref(0)
  const loading = ref(false)
  const query = reactive({ page: 1, pageSize: 10, ...defaultQuery })

  async function load() {
    loading.value = true
    try {
      const data = await fetcher({ ...query })
      rows.value = data?.list || []
      total.value = data?.total || 0
    } catch (err) {
      rows.value = []
      total.value = 0
      toast.error(err.message || '数据加载失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 改完筛选条件后调用。若当前不在第一页，改 page 会触发 watch 去加载；
   * 已经在第一页时 watch 不触发，需要显式 load 一次。
   */
  function search() {
    const alreadyFirstPage = query.page === 1
    query.page = 1
    if (alreadyFirstPage) load()
  }

  function reset(defaults = {}) {
    Object.assign(query, { page: 1, pageSize: query.pageSize, ...defaultQuery, ...defaults })
    search()
  }

  watch(
    () => query.page,
    () => load(),
  )

  watch(
    () => query.pageSize,
    () => {
      query.page = 1
      load()
    },
  )

  if (immediate) onMounted(load)

  return { rows, total, loading, query, load, search, reset }
}