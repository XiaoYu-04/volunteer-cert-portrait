import { upload } from '@/utils/request'

/**
 * 上传活动图片。返回 { fileUrl, fileName, fileSize, contentType }。
 */
export const uploadActivityImage = (file, onUploadProgress) =>
  upload('/v1/attachments/images', file, onUploadProgress)
