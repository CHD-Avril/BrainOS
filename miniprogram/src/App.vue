<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreInitialRoute } from '@/navigation/auth'

onLaunch(() => restoreInitialRoute())
</script>

<style lang="scss">
@import './uni.scss';
</style>
