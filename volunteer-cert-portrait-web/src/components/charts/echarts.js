/**
 * ECharts 按需注册。
 * 只引入本项目实际用到的图表与组件，避免打包进整包。
 * 新增图表类型时在这里补注册，否则运行时会静默不渲染。
 */
import * as echarts from 'echarts/core'
import {
  LineChart,
  BarChart,
  PieChart,
  GaugeChart,
  HeatmapChart,
  RadarChart,
} from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  VisualMapComponent,
  TitleComponent,
  RadarComponent,
  CalendarComponent,
} from 'echarts/components'
import { LabelLayout } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GaugeChart,
  HeatmapChart,
  RadarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  VisualMapComponent,
  TitleComponent,
  RadarComponent,
  CalendarComponent,
  LabelLayout,
  CanvasRenderer,
])

export default echarts