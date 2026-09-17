import { defineConfig } from 'vitepress'

// GitHub Pages 的"项目站点"地址形如 https://<用户名>.github.io/<仓库名>/，
// 所有静态资源必须以 /<仓库名>/ 为前缀，否则线上页面会因为找不到 CSS/JS 而一片空白。
// 本地开发/预览用默认的 '/'，CI 构建时由环境变量 DOCS_BASE 传入仓库名前缀。
// 以后如果换了自定义域名，或仓库名就是 <用户名>.github.io，把 DOCS_BASE 留空即可。
const base = process.env.DOCS_BASE ?? '/'

export default defineConfig({
  base,
  lang: 'zh-CN',
  title: '后端工程化学习',
  description: 'Spring Boot 多模块学习笔记：01 快速入门 · 02 配置管理',

  // 笔记里的链接指向仓库里的模块目录（../02-config 之类），
  // 在站点里不存在对应页面，因此关闭死链检查，避免构建失败。
  ignoreDeadLinks: true,

  markdown: {
    // 代码块显示行号，对照正文里的行号引用时方便
    lineNumbers: true
  },

  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '01 快速入门', link: '/01-quickstart' },
      { text: '02 配置管理', link: '/02-config' }
    ],

    sidebar: [
      {
        text: '学习笔记',
        items: [
          { text: '01 · 快速入门', link: '/01-quickstart' },
          { text: '02 · 配置管理', link: '/02-config' }
        ]
      }
    ],

    outline: {
      level: [2, 3],
      label: '本页目录'
    },

    search: {
      provider: 'local'
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },

    darkModeSwitchLabel: '主题',
    sidebarMenuLabel: '目录',
    returnToTopLabel: '回到顶部',

    footer: {
      message: '基于 VitePress 构建 · 笔记内容均为实测结果',
      copyright: 'beckend-learning'
    }
  }
})
