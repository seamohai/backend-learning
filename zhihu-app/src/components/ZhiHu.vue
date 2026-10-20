<template>
  <div class="container">
    <div style="margin-bottom: 20px">
      <select
        v-model="selectedCategory"
        @change="onChangeCategory"
        class="custom-select"
        >
        <option value="">全部</option>
        <option value="科技">科技</option>
        <option value="文化">文化</option>
      </select>
    </div>

    <div v-for="item in list" :key="item.id" class="card">
      <div class="left">
        <img :src="item.banner" alt="" />
      </div>
      <div class="right">
        <h3>{{ item.title }}</h3>
        <p>
          <span class="updated">{{ formatTime(item.updated) }}</span>
          <span>{{ item.viewCount }}次浏览</span>
        </p>
      </div>
      <button
        class="follow"
        :class="item.isFollowing === 'True' ? 'followed' : 'unfollow'"
        @click="toggleFollow(item)"
        >
        {{ item.isFollowing === 'True' ? '已关注' : '关注' }}
      </button>
    </div>

    <div class="pager" style="text-align: center">
      <button
        @click="prevPage"
        :disabled="isFirstPage"
        :class="{ disabled: isFirstPage }"
        >
        上一页
      </button>

      <button
        @click="nextPage"
        :disabled="isLastPage"
        :class="{ disabled: isLastPage }"
        >
        下一页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue'
  import axios from 'axios'

  interface Special {
    id: string
    title: string
    banner: string
    description: string
    isFollowing: string
    followersCount: number
    viewCount: number
    updated: number
    category: string
  }

  const BASE = 'http://localhost:8080'

  // updated 是 Unix 秒，转成 yyyy-MM-dd HH:mm:ss
  const formatTime = (sec: number): string => {
    const d = new Date(sec * 1000)
    const p = (n: number): string => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
           `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }

  const list = ref<Special[]>([])
  const selectedCategory = ref<string>('')

  const limit = ref<number>(3)
  const offset = ref<number>(0)
  const total = ref<number>(0)

  const isFirstPage = computed(() => offset.value === 0)
  const isLastPage = computed(() => offset.value + limit.value >= total.value)

  // 分类 + 分页统一走一个接口：category 为空串时后端不加分类条件
  const fetchList = (): void => {
    axios
      .get(`${BASE}/api/v1/special/list`, {
        params: {
          category: selectedCategory.value,
          limit: limit.value,
          offset: offset.value,
        },
      })
      .then((res) => {
        list.value = res.data.data.specials as Special[]
        total.value = res.data.data.total
      })
      .catch((err) => {
        console.log(err)
      })
  }

  // 关注按钮：先调后端落库，成功后再改本地状态
  const toggleFollow = (item: Special): void => {
    const next = item.isFollowing === 'True' ? 'False' : 'True'
    axios
      .put(`${BASE}/api/v1/special/${item.id}/follow`, null, {
        params: { isFollowing: next },
      })
      .then(() => {
        item.isFollowing = next
      })
      .catch((err) => {
        console.log(err)
      })
  }

  const onChangeCategory = (): void => {
    // 切分类必须回到第一页，否则会带着上一页的偏移去查，结果为空
    offset.value = 0
    fetchList()
  }

  const nextPage = (): void => {
    if (isLastPage.value) return
    offset.value += limit.value
    fetchList()
  }

  const prevPage = (): void => {
    if (offset.value === 0) return
    offset.value -= limit.value
    fetchList()
  }

  onMounted(() => {
    fetchList()
  })
</script>

<style scoped>
  .container {
    width: 70%;
    margin: 0 auto;
  }

  .card {
    display: flex;
    height: 160px;
    margin-bottom: 15px;
    border: 1px solid #ccc;
    box-shadow: 10px 5px 5px #ddd;
  border-radius: 8px;
  position: relative;
}
.left {
  flex: 1;
}

.right {
  flex: 4;
  padding: 10px;
}

.left img {
  width: 100%;
  height: 100%;
  border-top-left-radius: 8px;
  border-bottom-left-radius: 8px;
}

p span {
  font-size: 13px;
  color: #333;
}

.updated {
  margin-right: 20px;
}

.follow {
  position: absolute;
  right: 20px;
  top: 20px;
  padding: 5px 12px;
  font-size: 14px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.followed {
  background-color: #f6f6f6;
  color: #716c6c;
  border-color: #e2e2e2;
}
.followed:hover {
  background-color: #ececec;
}
.unfollow {
  background-color: #eff4fe;
  color: #3670ee;
  border-color: #d6e2fd;
}
.unfollow:hover {
  background-color: #e0ebff;
  border-color: #b9d0fb;
}

.pager button {
  padding: 10px 20px;
  margin: 10px;
  border: none;
  border-radius: 5px;
  background-color: #007bff;
  color: white;
  cursor: pointer;
}

.pager button:hover {
  background-color: #0056b3;
}

.pager button:disabled,
.pager button.disabled {
  background-color: #d6d6d6;
  color: #888;
  cursor: not-allowed;
}

.custom-select {
  background-color: #ffffff;
  border: 2px solid #007bff;
  border-radius: 5px;
  padding: 10px 40px 10px 15px; /* Padding with space for custom arrow */
  font-size: 16px;
  color: #333;
  cursor: pointer;
  position: relative;
  outline: none;
  transition: all 0.3s ease;
}

.custom-select option {
  color: #333;
  background-color: #fff;
}
</style>
