import { createApp } from 'vue'
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMenu,
  ElMenuItem,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/menu/style/css'
import 'element-plus/es/components/menu-item/style/css'
import App from './App.vue'
import { pinia } from './pinia'
import { router } from './router'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElAlert)
app.use(ElButton)
app.use(ElForm)
app.use(ElFormItem)
app.use(ElIcon)
app.use(ElInput)
app.use(ElMenu)
app.use(ElMenuItem)
app.mount('#app')
