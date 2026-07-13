// 加密记事本控制台入口。运行时由 Halo 提供 window.HaloUiShared / window.Vue / window.axios。
// 与现有产物保持一致：definePlugin 注册路由，组件用动态 import 懒加载。
export default (window as any).HaloUiShared.definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/password-book",
        name: "PasswordBook",
        component: () => import("./views/HomeView.vue"),
        meta: {
          title: "加密记事本",
          searchable: true,
          permissions: ["plugin:password-book:view"],
          menu: {
            name: "加密记事本",
            group: "工具",
            icon: "lock",
            priority: 21,
          },
        },
      },
    },
  ],
  extensionPoints: {},
});
