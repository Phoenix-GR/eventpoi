/**
 * EventExcelHelper.java
 */
package com.dc.eventpoi;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.hssf.usermodel.HSSFPictureData;
import org.apache.poi.hssf.usermodel.HSSFShape;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTMarker;

import com.alibaba.fastjson.JSON;
import com.dc.eventpoi.core.entity.ExcelCell;
import com.dc.eventpoi.core.entity.ExcelRow;
import com.dc.eventpoi.core.entity.ExportExcelCell;
import com.dc.eventpoi.core.enums.FileType;
import com.dc.eventpoi.core.inter.CallBackCellStyle;
import com.dc.eventpoi.core.inter.ExcelEventStream;
import com.dc.eventpoi.core.inter.RowCallBack;

/**
 * excel操作
 *
 * @author beijing-penguin
 */
public class ExcelHelper {

	/**
	 * 导出表格 以及 列表数据
	 * @param excelTemplateStream 模板文件流
	 * @param listAndTableDataList 包含列表数据集合 和 表格数据对象
	 * @return byte[]
	 * @throws Exception
	 */
	public static byte[] exportTableExcel(InputStream excelTemplateStream, List<Object> listAndTableDataList) throws Exception {

		ByteArrayOutputStream templateByteStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024 * 4];
		int n = 0;
		while (-1 != (n = excelTemplateStream.read(buffer))) {
			templateByteStream.write(buffer, 0, n);
		}

		FileType fileType = judgeFileType(new ByteArrayInputStream(templateByteStream.toByteArray()));
		Workbook tempWb = null;
		if (fileType == FileType.XLSX) {
			tempWb = new XSSFWorkbook(new ByteArrayInputStream(templateByteStream.toByteArray()));
		} else {
			tempWb = (HSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(templateByteStream.toByteArray()));
		}

		Sheet tempsheet = tempWb.getSheetAt(0);
		XSSFDrawing patriarch = (XSSFDrawing) tempsheet.createDrawingPatriarch();
		int rowNum = tempsheet.getPhysicalNumberOfRows();

		int startRow = 0;
		Short rowheight = 0;
		Map<Integer,Integer> skipIndexMap = new HashMap<Integer, Integer>();
		for (Object obj : listAndTableDataList) {
			List<ExportExcelCell> keyCellList = new ArrayList<ExportExcelCell>();
			if(obj instanceof Collection) {
				List<?> list = (ArrayList<?>)obj;
				if(list.size() != 0) {
					Object data1 = list.get(0);
					for (int i = 0; i < rowNum; i++) {
						if(skipIndexMap.size()!=0) {
							boolean flag = false;
							for (Entry<Integer, Integer> entry : skipIndexMap.entrySet()) {
								int start = entry.getKey();
								int end = entry.getValue();
								if(i>=start && i<=end) {
									flag = true;
									break;
								}
							}

							if(flag) {
								continue;
							}
						}

						Row row = tempsheet.getRow(i);
						int cellNum = row.getPhysicalNumberOfCells();

						for (int k = 0; k < cellNum; k++) {
							Cell cell = row.getCell(k);
							if(cell!=null) {
								String vv = cell.getStringCellValue();
								if (vv != null && vv.startsWith("${")) {
									String keyName = vv.substring(vv.indexOf("${") + 2, vv.lastIndexOf("}"));
									if(FieldUtils.getField(data1.getClass(), keyName, true) != null) {
										startRow = i;
										rowheight = row.getHeight();
										ExportExcelCell cc = new ExportExcelCell((short) k, vv, cell.getCellStyle());
										keyCellList.add(cc);
									}
								}
							}
						}
					}
				}

				tempsheet.removeRow(tempsheet.getRow(startRow));
				tempsheet.shiftRows(startRow + 1, startRow + 1 + 1, -1);
				if(keyCellList.size() != 0) {
					skipIndexMap.put(startRow, startRow+list.size()-1);
					int listIndex = 0;
					for (int j = startRow; j < startRow + list.size(); j++) {
						tempsheet.shiftRows(startRow+listIndex,  tempsheet.getLastRowNum(), 1,true,false);
						Row row2 = tempsheet.createRow(startRow+listIndex);
						row2.setHeight(rowheight);
						Object data1 = list.get(listIndex);
						listIndex = listIndex+1;

						for (int k = 0; k < keyCellList.size(); k++) {

							ExportExcelCell cellField = keyCellList.get(k);
							String excelField = cellField.getValue().substring(cellField.getValue().indexOf("${") + 2, cellField.getValue().lastIndexOf("}"));
							Field dataField = FieldUtils.getField(data1.getClass(), excelField, true);
							if(dataField != null && !Modifier.isStatic(dataField.getModifiers())) {
								Cell cell = row2.createCell(cellField.getIndex(), CellType.STRING);
								Object value = dataField.get(data1);
								if (value != null && value.toString().trim().length() > 0) {
									if (value instanceof byte[]) {
										if (getImageType((byte[]) value) != null) {
											XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, k, j + startRow, k + 1, j + 1 + startRow);
											int picIndex = tempWb.addPicture((byte[]) value, HSSFWorkbook.PICTURE_TYPE_JPEG);
											patriarch.createPicture(anchor, picIndex);
										} else {
											cell.setCellValue(new String((byte[]) value));
										}
									} else {
										cell.setCellValue(String.valueOf(value));
									}
								}
							}
						}
					}
					rowNum = rowNum + list.size();
				}
			}else {
				for (int i = 0; i < rowNum; i++) {
					if(skipIndexMap.size()!=0) {
						boolean flag = false;
						for (Entry<Integer, Integer> entry : skipIndexMap.entrySet()) {
							int start = entry.getKey();
							int end = entry.getValue();
							if(i>=start && i<=end) {
								flag = true;
								break;
							}
						}

						if(flag) {
							continue;
						}
					}
					Row row = tempsheet.getRow(i);
					if(row!=null) {
						int cellNum  = row.getPhysicalNumberOfCells();
						for (int k = 0; k < cellNum; k++) {
							Cell cell = row.getCell(k);
							if (cell != null) {
								String cellValue = cell.getStringCellValue();
								if (cellValue != null && cellValue.contains("${")) {

									String excelField = cellValue.substring(cellValue.indexOf("${") + 2, cellValue.lastIndexOf("}"));
									String excelFieldSrcKeyword = cellValue.substring(cellValue.indexOf("${") , cellValue.lastIndexOf("}")+1);
									Field field = FieldUtils.getField(obj.getClass(), excelField, true);
									if (field != null && !Modifier.isStatic(field.getModifiers()) &&  field.get(obj) != null) {
										Object value = field.get(obj);
										if (value instanceof byte[]) {
											if (getImageType((byte[]) value) != null) {
												XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, k, rowNum, k + 1, rowNum+1);
												int picIndex = tempWb.addPicture((byte[]) value, HSSFWorkbook.PICTURE_TYPE_JPEG);
												patriarch.createPicture(anchor, picIndex);
											} else {
												cell.setCellValue(new String((byte[]) value));
											}
										} else {
											cellValue = cellValue.replace(excelFieldSrcKeyword, String.valueOf(value));
											cell.setCellValue(cellValue);
										}
									}else {
										cellValue = cellValue.replace(excelFieldSrcKeyword, "");
										cell.setCellValue(cellValue);
									}
								}
							}
						}
					}
				}
			}
		}

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		tempWb.write(byteStream);
		byteStream.flush();
		byteStream.close();
		tempWb.close();
		return byteStream.toByteArray();
	}

	/**
	 * 解析Excel为对象集合
	 *
	 * @param excelDataSourceStream Excel原数据流
	 * @param excelTemplateStream   模版数据流
	 * @param clazz                 clazz
	 * @param imageRead             是否支持图片格式读取（开启此功能，性能降低，内存消耗增加。）
	 * @param <T>                   对象
	 * @return 对象集合
	 * @throws Exception IOException
	 */
	public static <T> List<T> parseExcelToObject(InputStream excelDataSourceStream, InputStream excelTemplateStream, Class<T> clazz, boolean imageRead) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024 * 4];
		int n = 0;
		while (-1 != (n = excelDataSourceStream.read(buffer))) {
			output.write(buffer, 0, n);
		}

		// 创建Workbook  
		Workbook wb = null;
		// 创建sheet
		Sheet sheet = null;
		FileType fileType = ExcelHelper.judgeFileType(new ByteArrayInputStream(output.toByteArray()));
		switch (fileType) {
		case XLS:
			wb = (HSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()));
			break;
		case XLSX:
			wb = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()));
			break;
		default:
			throw new Exception("filetype is unsupport");
		}
		//获取excel sheet总数  
		int sheetNumbers = wb.getNumberOfSheets();

		Map<String, byte[]> map = new HashMap<String, byte[]>();
		// 循环sheet  
		for (int i = 0; i < sheetNumbers; i++) {

			sheet = wb.getSheetAt(i);

			switch (fileType) {
			case XLS:
				map.putAll(getXlsPictures(i, (HSSFSheet) sheet));
				break;
			case XLSX:
				map.putAll(getXlsxPictures(i, (XSSFSheet) sheet));
				break;
			default:
				throw new Exception("filetype is unsupport");
			}
		}
		wb.close();

		List<ExcelRow> dataList = ExcelHelper.parseExcelRowList(new ByteArrayInputStream(output.toByteArray()));
		List<ExcelRow> templeteList = ExcelHelper.parseExcelRowList(excelTemplateStream);
		checkTemplete(dataList, templeteList);

		if (map.size() > 0) {
			for (ExcelRow excelRow : dataList) {
				int rowIndex = excelRow.getRowIndex();
				int sheetIndex = excelRow.getSheetIndex();
				List<ExcelCell> cellList = excelRow.getCellList();
				for (Entry<String, byte[]> entry : map.entrySet()) {
					int img_sheetIndex = Integer.parseInt(entry.getKey().split("-")[0]);
					int img_rowIndex = Integer.parseInt(entry.getKey().split("-")[1]);
					int img_cellIndex = Integer.parseInt(entry.getKey().split("-")[2]);
					if (rowIndex == img_rowIndex && img_sheetIndex == sheetIndex) {
						ExcelCell imgCell = new ExcelCell((short) img_sheetIndex, entry.getValue());
						cellList.add(img_cellIndex, imgCell);
						break;
					}
				}
			}
		}
		return ExcelHelper.parseExcelToObject(dataList, templeteList, clazz);
	}

	private static Map<String, byte[]> getXlsxPictures(int sheetIndex, XSSFSheet sheet) throws Exception {
		Map<String, byte[]> map = new HashMap<String, byte[]>();
		List<POIXMLDocumentPart> list = sheet.getRelations();
		for (POIXMLDocumentPart part : list) {
			if (part instanceof XSSFDrawing) {
				XSSFDrawing drawing = (XSSFDrawing) part;
				List<XSSFShape> shapes = drawing.getShapes();
				for (XSSFShape shape : shapes) {
					XSSFPicture picture = (XSSFPicture) shape;
					XSSFClientAnchor anchor = picture.getPreferredSize();
					CTMarker marker = anchor.getFrom();
					String key = sheetIndex + "-" + marker.getRow() + "-" + marker.getCol();
					map.put(key, picture.getPictureData().getData());
				}
			}
		}
		return map;
	}

	private static Map<String, byte[]> getXlsPictures(int sheetIndex, HSSFSheet sheet) {
		Map<String, byte[]> map = new HashMap<String, byte[]>();
		List<HSSFShape> list = sheet.getDrawingPatriarch().getChildren();
		for (HSSFShape shape : list) {
			if (shape instanceof HSSFPicture) {
				HSSFPicture picture = (HSSFPicture) shape;
				HSSFClientAnchor cAnchor = picture.getClientAnchor();
				HSSFPictureData pdata = picture.getPictureData();
				String key = sheetIndex + "-" + cAnchor.getRow1() + "-" + cAnchor.getCol1(); // 行号-列号
				map.put(key, pdata.getData());
			}
		}
		return map;
	}

	public static <T> List<T> parseExcelToObject(InputStream excelDataSourceStream, InputStream excelTemplateStream, Class<T> clazz) throws Exception {
		List<ExcelRow> dataList = ExcelHelper.parseExcelRowList(excelDataSourceStream);
		List<ExcelRow> templeteList = ExcelHelper.parseExcelRowList(excelTemplateStream);
		checkTemplete(dataList, templeteList);
		return ExcelHelper.parseExcelToObject(dataList, templeteList, clazz);
	}

	/**
	 * @param fileList     数据文件
	 * @param templeteList 模板文件
	 * @param clazz        类对象
	 * @param <T>          T
	 * @return 集合
	 * @throws Exception IOException
	 * @author beijing-penguin
	 */
	public static <T> List<T> parseExcelToObject(List<ExcelRow> fileList, List<ExcelRow> templeteList, Class<T> clazz) throws Exception {
		List<T> rtn = new ArrayList<T>();
		List<ExcelCell> tempFieldList = new ArrayList<ExcelCell>();
		int size = fileList.size();
		int x = 0;
		int startRow = 0;
		for (int i = 0; i < templeteList.size(); i++) {
			if (templeteList.get(i).getCellList().get(0).getValue().startsWith("$")) {
				startRow = templeteList.get(i).getRowIndex();
				short sheetIndex = templeteList.get(i).getSheetIndex();
				tempFieldList = templeteList.get(i).getCellList();

				for (int j = (x + startRow); j < size; j++) {
					ExcelRow row = fileList.get(j);
					int rowIndex = row.getRowIndex();
					if (rowIndex >= startRow && row.getSheetIndex() == sheetIndex) {
						x++;
						T obj = clazz.getDeclaredConstructor().newInstance();
						List<ExcelCell> fieldList = row.getCellList();
						for (ExcelCell fieldCell : fieldList) {
							for (ExcelCell tempCell : tempFieldList) {
								if (fieldCell.getIndex() == tempCell.getIndex()) {
									for (Field field : FieldUtils.getAllFields(clazz)) {
										if (!Modifier.isStatic(field.getModifiers())) {
											if (tempCell.getValue().contains(field.getName())) {
												field.setAccessible(true);
												if (fieldCell.getImgBytes() != null) {
													//Object vall = getValueByFieldType(fieldCell.getImgBytes(), field.getType());
													field.set(obj, fieldCell.getImgBytes());
												} else {
													Object vall = getValueByFieldType(fieldCell.getValue(), field.getType());
													field.set(obj, vall);
												}
												break;
											}
										}
									}
								}
							}
						}
						rtn.add(obj);
					}
				}
			}
		}

		return rtn;
	}

	/**
	 * 读取所有sheet数据
	 *
	 * @param baytes 文件
	 * @return List
	 * @throws Exception IOException
	 * @author dc
	 */
	public static List<ExcelRow> parseExcelRowList(byte[] baytes) throws Exception {
		return parseExcelRowList(new ByteArrayInputStream(baytes));
	}

	/**
	 * 读取excel指定sheet数据
	 *
	 * @param baytes     文件数据
	 * @param sheetIndex sheet工作簿索引号
	 * @return List
	 * @throws Exception IOException
	 * @author dc
	 */
	public static List<ExcelRow> parseExcelRowList(byte[] baytes, Integer sheetIndex) throws Exception {
		return parseExcelRowList(new ByteArrayInputStream(baytes), sheetIndex);
	}

	/**
	 * 读取excel指定sheet数据
	 *
	 * @param file       文件
	 * @param sheetIndex sheet工作簿索引号
	 * @return List
	 * @throws Exception IOException
	 * @author dc
	 */
	public static List<ExcelRow> parseExcelRowList(File file, Integer sheetIndex) throws Exception {
		return parseExcelRowList(new FileInputStream(file), sheetIndex);
	}

	/**
	 * 读取所有sheet数据
	 *
	 * @param file 文件
	 * @return List
	 * @throws Exception IOException
	 * @author dc
	 */
	public static List<ExcelRow> parseExcelRowList(File file) throws Exception {
		return parseExcelRowList(new FileInputStream(file), null);
	}

	/**
	 * 读取指定sheet数据
	 *
	 * @param inputSrc   excel源文件input输入流
	 * @param sheetIndex sheet工作簿索引号
	 * @return List
	 * @throws Exception IOException
	 * @author dc
	 */
	public static List<ExcelRow> parseExcelRowList(InputStream inputSrc, Integer sheetIndex) throws Exception {
		List<ExcelRow> fileList = new ArrayList<ExcelRow>();
		ExcelEventStream fileStream = null;
		try {
			fileStream = ExcelEventStream.readExcel(inputSrc);
			fileStream.sheetAt(sheetIndex).rowStream(new RowCallBack() {
				@Override
				public void getRow(ExcelRow row) {
					fileList.add(row);
				}
			});
		} catch (Exception e) {
			throw e;
		} finally {
			if (fileStream != null) {
				fileStream.close();
			}
		}
		return fileList;
	}

	/**
	 * @param inputSrc excel源文件input输入流
	 * @return List
	 * @throws Exception IOException
	 * @author dc
	 */
	public static List<ExcelRow> parseExcelRowList(InputStream inputSrc) throws Exception {
		return parseExcelRowList(inputSrc, null);
	}

	/**
	 * 模板与数据文件检查
	 *
	 * @param fileList     原始上传文件
	 * @param templeteList 模板文件
	 * @throws Exception IOException
	 * @author beijing-penguin
	 */
	public static void checkTemplete(List<ExcelRow> fileList, List<ExcelRow> templeteList) throws Exception {
		for (int i = 0; i < templeteList.size(); i++) {
			ExcelRow row = templeteList.get(i);
			List<ExcelCell> excelCell = row.getCellList();
			if (!excelCell.get(0).getValue().startsWith("${")) {
				if (!JSON.toJSONString(templeteList.get(i)).equals(JSON.toJSONString(fileList.get(i)))) {
					throw new Exception("fileList is not the same as templeteList[读取文件的excel头信息和模板头信息不匹配，文件格式不一致]");
				}
			} else {
				break;
			}
		}
	}

	/**
	 * @param value     任意数据类型对象
	 * @param fieldType 转化后的类型
	 * @return Object
	 * @throws Exception IOException
	 * @author dc
	 */
	public static Object getValueByFieldType(Object value, Class<?> fieldType) throws Exception {
		if (value == null) {
			return null;
		}
		String v = String.valueOf(value);
		String type = fieldType.getSimpleName();
		if (type.equals("String")) {
			return v;
		} else if (v.trim().length() == 0) {
			return null;
		} else if (type.equals("Integer") || type.equals("int")) {
			return Integer.parseInt(v);
		} else if (type.equals("Long") || type.equals("long")) {
			return Long.parseLong(v);
		} else if (type.equals("Double") || type.equals("double")) {
			return Double.parseDouble(v);
		} else if (type.equals("Short") || type.equals("short")) {
			return Short.parseShort(v);
		} else if (type.equals("Float") || type.equals("float")) {
			return Float.parseFloat(v);
		} else if (type.equals("Byte") || type.equals("byte")) {
			return Byte.parseByte(v);
		} else if (type.equals("Byte[]") || type.equals("byte[]")) {
			return v.getBytes();
		} else if (type.equals("Boolean") || type.equals("boolean")) {
			return Boolean.parseBoolean(v);
		} else if (type.equals("BigDecimal")) {
			return new BigDecimal(v);
		} else if (type.equals("BigInteger")) {
			return new BigInteger(v);
		} else if (type.equals("Date")) {
			SimpleDateFormat sdf = new SimpleDateFormat(getDateFormat(v));
			//不允许底层java自动日期进行计算，直接抛出异常
			sdf.setLenient(false);
			Date date = sdf.parse(v);
			return date;
		}
		throw new Exception(type + " is unsupported");
	}


	/**
	 * 常规自动日期格式识别
	 *
	 * @param str 时间字符串
	 * @return Date
	 * @author dc
	 */
	public static String getDateFormat(String str) {
		boolean year = false;
		Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");
		if (pattern.matcher(str.substring(0, 4)).matches()) {
			year = true;
		}
		StringBuilder sb = new StringBuilder();
		int index = 0;
		if (!year) {
			if (str.contains("月") || str.contains("-") || str.contains("/")) {
				if (Character.isDigit(str.charAt(0))) {
					index = 1;
				}
			} else {
				index = 3;
			}
		}
		for (int i = 0; i < str.length(); i++) {
			char chr = str.charAt(i);
			if (Character.isDigit(chr)) {
				if (index == 0) {
					sb.append("y");
				} else if (index == 1) {
					sb.append("M");
				} else if (index == 2) {
					sb.append("d");
				} else if (index == 3) {
					sb.append("H");
				} else if (index == 4) {
					sb.append("m");
				} else if (index == 5) {
					sb.append("s");
				} else if (index == 6) {
					sb.append("S");
				}
			} else {
				if (i > 0) {
					char lastChar = str.charAt(i - 1);
					if (Character.isDigit(lastChar)) {
						index++;
					}
				}
				sb.append(chr);
			}
		}
		return sb.toString();
	}

	/**
	 * 删除模板中的固定格式
	 *
	 * @param inputSrc   源模板文件
	 * @param sheetIndex 工作簿索引下标
	 * @return ByteArrayOutputStream
	 * @throws Exception IOException
	 */
	public static ByteArrayOutputStream deleteTempleteFormat(InputStream inputSrc, int sheetIndex) throws Exception {
		Workbook workbook = WorkbookFactory.create(inputSrc);
		Sheet sheet = workbook.getSheetAt(sheetIndex);
		int totalRow = sheet.getPhysicalNumberOfRows();
		for (int i = sheet.getFirstRowNum(); i < totalRow; i++) {
			Row row = sheet.getRow(i);
			if (row != null) {
				for (int j = row.getFirstCellNum(), totalCell = row.getPhysicalNumberOfCells(); j < totalCell; j++) {
					Cell cell = row.getCell(j);
					if (cell != null) {
						//cell.setCellType(CellType.STRING);
						String value = cell.getStringCellValue();
						if (value != null && value.startsWith("${")) {
							sheet.removeRow(row);
							sheet.shiftRows(i + 1, i + 1 + 1, -1);
							break;
						}
					}
				}
			}
		}
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		workbook.write(os);
		os.flush();
		return os;
	}

	/**
	 * 导出zip文件,或者导出xlxs文件
	 *
	 * @param template          模板文件数据
	 * @param dataList          对象数据集合
	 * @param sheetIndex        工作簿
	 * @param callBackCellStyle 单元格样式
	 * @return byte[]
	 * @throws Exception IOException
	 */
	/**
	 * 导出zip文件,或者导出xlxs文件
	 *
	 * @param template          模板文件数据
	 * @param dataList          对象数据集合
	 * @param sheetIndex        工作簿
	 * @param callBackCellStyle 单元格样式
	 * @param isZip             是否压缩zip
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] exportExcel(byte[] template, List<?> dataList, int sheetIndex, CallBackCellStyle callBackCellStyle, boolean isZip) throws Exception {
		FileType fileType = judgeFileType(new ByteArrayInputStream(template));
		String templeteFileName = null;
		Workbook tempWb = null;
		if (fileType == FileType.XLSX) {
			templeteFileName = "file.xlxs";
			tempWb = new XSSFWorkbook(new ByteArrayInputStream(template));
		} else {
			tempWb = (HSSFWorkbook) WorkbookFactory.create(new ByteArrayInputStream(template));
			templeteFileName = "file.xls";
		}

		int dataTotal = 45000;
		if (isZip) {
			if (fileType == FileType.XLSX) {
				dataTotal = 30000;//3w
			} else {
				dataTotal = 30000;//3w
			}
		} else {
			dataTotal = Integer.MAX_VALUE;
		}
		//int sheetNumbers = tempWb.getNumberOfSheets();
		List<ExportExcelCell> keyCellList = new ArrayList<ExportExcelCell>();
		Integer startRow = null;
		Short rowheight = null;
		Sheet tempsheet = tempWb.getSheetAt(sheetIndex);
		int rowIndex = tempsheet.getPhysicalNumberOfRows();
		for (int j = 0; j < rowIndex; j++) {
			int cellIndex = tempsheet.getRow(j).getPhysicalNumberOfCells();
			for (int k = 0; k < cellIndex; k++) {
				Cell cell = tempsheet.getRow(j).getCell(k);
				String vv = cell.getStringCellValue();
				if (vv != null && vv.startsWith("${")) {
					startRow = j;
					rowheight = tempsheet.getRow(j).getHeight();
					ExportExcelCell cc = new ExportExcelCell((short) k, vv, cell.getCellStyle());
					keyCellList.add(cc);
				}
			}
		}
		tempWb.close();

		int len = dataList.size();
		int l = len / dataTotal + (len % dataTotal != 0 ? 1 : 0);
		Map<String, byte[]> fileDataMap = new LinkedHashMap<String, byte[]>();
		for (int i = 0; i < l; i++) {
			Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
			Sheet sheet = workbook.getSheetAt(sheetIndex);
			XSSFDrawing patriarch = (XSSFDrawing) sheet.createDrawingPatriarch();
			CellStyle cellStyle = workbook.createCellStyle();
			//            cellStyle.setAlignment(HorizontalAlignment.CENTER); // 水平居中
			//            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER); // 上下居中
			//            cellStyle.setBorderTop(BorderStyle.THIN);
			//            cellStyle.setBorderBottom(BorderStyle.THIN);
			//            cellStyle.setBorderLeft(BorderStyle.THIN);
			//            cellStyle.setBorderRight(BorderStyle.THIN);

			for (int j = i * dataTotal; j < (i + 1) * dataTotal; j++) {
				if (j >= len) {
					break;
				}
				Row row = sheet.createRow(j + startRow - i * dataTotal);
				row.setHeight(rowheight);
				Object obj = dataList.get(j);
				for (int k = 0; k < keyCellList.size(); k++) {
					ExportExcelCell cellField = keyCellList.get(k);
					String excelField = cellField.getValue().substring(cellField.getValue().indexOf("${") + 2, cellField.getValue().lastIndexOf("}"));
					Field[] fieldArr = FieldUtils.getAllFields(obj.getClass());
					for (Field field : fieldArr) {
						if (!Modifier.isStatic(field.getModifiers()) && field.getName().equals(excelField)) {
							Cell cell = row.createCell(cellField.getIndex(), CellType.STRING);
							field.setAccessible(true);
							Object value = field.get(obj);
							if (value != null && value.toString().trim().length() > 0) {

								if (value instanceof byte[]) {
									if (getImageType((byte[]) value) != null) {
										XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, k, j + startRow, k + 1, j + 1 + startRow);
										int picIndex = workbook.addPicture((byte[]) value, HSSFWorkbook.PICTURE_TYPE_JPEG);
										patriarch.createPicture(anchor, picIndex);
									} else {
										cell.setCellValue(new String((byte[]) value));
									}
								} else {
									cell.setCellValue(String.valueOf(value));
								}
								if (callBackCellStyle != null) {
									callBackCellStyle.callBack(cellStyle);
									cell.setCellStyle(cellStyle);
								}
							}
						}
					}
				}
			}
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			workbook.write(byteStream);
			byteStream.flush();
			byteStream.close();
			String newName = "";
			if (templeteFileName.lastIndexOf(".") != -1) {
				newName = templeteFileName.substring(0, templeteFileName.lastIndexOf(".")) + "_" + i + templeteFileName.substring(templeteFileName.lastIndexOf("."));
			} else {
				newName = templeteFileName + "_" + i;
			}
			fileDataMap.put(newName, byteStream.toByteArray());
		}
		if (isZip) {
			throw new Exception("zip is unsupport");
			//return ZipUtils.batchCompress(fileDataMap);
		} else {
			return fileDataMap.values().iterator().next();
		}
	}

	/**
	 * 导出zip文件,或者导出xlxs文件
	 *
	 * @param templete 模板文件数据
	 * @param data     对象数据
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] exportTableExcel(byte[] templete, Object data) throws Exception {
		return exportTableExcel(templete, data, 0);
	}

	/**
	 * 导出zip文件,或者导出xlxs文件
	 *
	 * @param templete   模板文件数据
	 * @param data       对象数据
	 * @param sheetIndex 工作簿
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] exportTableExcel(byte[] templete, Object data, int sheetIndex) throws Exception {
		List<ExcelRow> rowList = new ArrayList<ExcelRow>();
		List<ExcelCell> keyCellList = new ArrayList<ExcelCell>();
		ExcelEventStream fileStream = ExcelEventStream.readExcel(templete);
		fileStream.sheetAt(sheetIndex).rowStream(new RowCallBack() {
			@Override
			public void getRow(ExcelRow row) {
				rowList.add(row);
			}
		});
		for (int i = 0; i < rowList.size(); i++) {
			ExcelRow row = rowList.get(i);
			List<ExcelCell> cellList = row.getCellList();
			for (int j = 0; j < cellList.size(); j++) {
				ExcelCell cell = cellList.get(j);
				if (cell.getValue().startsWith("${")) {
					keyCellList.addAll(cellList);
					break;
				}
			}
		}
		Map<String, byte[]> fileDataMap = new LinkedHashMap<String, byte[]>();
		Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(templete));
		Sheet sheet = workbook.getSheetAt(sheetIndex);

		for (int j = 0; j <= sheet.getLastRowNum(); j++) {
			Row row = sheet.getRow(j);
			for (int cellnum = 0; cellnum <= row.getLastCellNum(); cellnum++) {
				Cell cell = row.getCell(cellnum);
				if (cell != null) {
					String cellValue = cell.getStringCellValue();
					if (cellValue != null && cellValue.startsWith("${")) {
						String excelField = cellValue.substring(cellValue.indexOf("${") + 2, cellValue.lastIndexOf("}"));
						Field[] fieldArr = FieldUtils.getAllFields(data.getClass());
						for (Field field : fieldArr) {
							if (!Modifier.isStatic(field.getModifiers()) && field.getName().equals(excelField)) {
								field.setAccessible(true);
								Object value = field.get(data);
								if (value == null) {
									value = "";
								}
								cell.setCellValue(String.valueOf(value));
							}
						}
					}
				}
			}
		}
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		workbook.write(byteStream);
		byteStream.flush();
		byteStream.close();
		String newName = "temp";
		fileDataMap.put(newName, byteStream.toByteArray());
		return fileDataMap.values().iterator().next();
	}

	public static String getImageType(byte[] b10) {
		byte b0 = b10[0];
		byte b1 = b10[1];
		byte b2 = b10[2];
		byte b3 = b10[3];
		byte b6 = b10[6];
		byte b7 = b10[7];
		byte b8 = b10[8];
		byte b9 = b10[9];
		if (b0 == (byte) 'G' && b1 == (byte) 'I' && b2 == (byte) 'F') {
			return "gif";
		} else if (b1 == (byte) 'P' && b2 == (byte) 'N' && b3 == (byte) 'G') {
			return "png";
		} else if (b6 == (byte) 'J' && b7 == (byte) 'F' && b8 == (byte) 'I' && b9 == (byte) 'F') {
			return "jpg";
		} else {
			return null;
		}
	}


	/**
	 * 导出zip数据
	 *
	 * @param templete          模板数据
	 * @param dataList          对象数据集合
	 * @param callBackCellStyle 单元格样式
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] exportExcel(byte[] templete, List<?> dataList, CallBackCellStyle callBackCellStyle) throws Exception {
		return exportExcel(templete, dataList, 0, callBackCellStyle, false);
	}

	/**
	 * 导出zip数据
	 *
	 * @param templeteFileName 模板文件名
	 * @param templete         templete
	 * @param dataList         dataList
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] exportExcel(String templeteFileName, byte[] templete, List<?> dataList) throws Exception {
		return exportExcel(templete, dataList, 0, null, true);
	}

	/**
	 * 导出zip数据
	 *
	 * @param templete 模板文件流
	 * @param dataList 对象数据集合
	 * @param isZip    是否压缩
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] exportExcel(byte[] templete, List<?> dataList, boolean isZip) throws Exception {
		return exportExcel(templete, dataList, 0, null, isZip);
	}

	/**
	 * 流转byte[]
	 *
	 * @param is is
	 * @return byte[]
	 * @throws Exception IOException
	 */
	public static byte[] inputStreamToByte(InputStream is) throws Exception {
		BufferedInputStream bis = new BufferedInputStream(is);
		byte[] a = new byte[1000];
		int len = 0;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		while ((len = bis.read(a)) != -1) {
			bos.write(a, 0, len);
		}
		bis.close();
		bos.close();
		return bos.toByteArray();
	}

	/**
	 * 判断文件类型
	 *
	 * @param inp 数据流
	 * @return FileType
	 * @throws Exception IOException
	 * @author beijing-penguin
	 */
	public static FileType judgeFileType(InputStream inp) throws Exception {
		InputStream is = FileMagic.prepareToCheckMagic(inp);
		FileMagic fm = FileMagic.valueOf(is);

		switch (fm) {
		case OLE2:
			return FileType.XLS;
		case OOXML:
			return FileType.XLSX;
		default:
			throw new IOException("Your InputStream was neither an OLE2 stream, nor an OOXML stream");
		}
	}

	/**
	 * 获取cell值
	 *
	 * @param cellList    cell集合
	 * @param cellIndex   索引号
	 * @param returnClass 返回类型
	 * @param <T>         返回类型
	 * @return T
	 * @throws Exception IOException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getValueBy(List<ExcelCell> cellList, int cellIndex, Class<? extends T> returnClass) throws Exception {
		for (int i = 0; i < cellList.size(); i++) {
			ExcelCell cell = cellList.get(i);
			if (cell.getIndex() == cellIndex) {
				return (T) getValueByFieldType(cell.getValue(), returnClass);
			}
		}
		return null;
	}

	/**
	 * 获取cell值
	 *
	 * @param cellList  cell集合
	 * @param cellIndex 索引号
	 * @return String
	 * @throws Exception IOException
	 */
	public static String getValueBy(List<ExcelCell> cellList, int cellIndex) throws Exception {
		return getValueBy(cellList, cellIndex, String.class);
	}

	/**
	 * 获取值
	 *
	 * @param rowList     行集合
	 * @param rowIndex    行下标
	 * @param cellIndex   列下标
	 * @param returnClass 返回值类型
	 * @param <T>         返回类型
	 * @return T
	 * @throws Exception IOException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getValueBy(List<ExcelRow> rowList, int rowIndex, int cellIndex, Class<? extends T> returnClass) throws Exception {
		for (int i = 0; i < rowList.size(); i++) {
			ExcelRow row = rowList.get(i);
			if (row.getRowIndex() > rowIndex) {
				break;
			}
			if (row.getRowIndex() == rowIndex) {
				List<ExcelCell> cellList = row.getCellList();
				for (int j = 0; j < cellList.size(); j++) {
					ExcelCell cell = cellList.get(j);
					if (cell.getIndex() == cellIndex) {
						return (T) getValueByFieldType(cell.getValue(), returnClass);
					}
				}
			}
		}
		return null;
	}

	/**
	 * 获取值
	 *
	 * @param rowList   行集合
	 * @param rowIndex  行下标
	 * @param cellIndex 列下标
	 * @return String
	 * @throws Exception IOException
	 */
	public static String getValueBy(List<ExcelRow> rowList, int rowIndex, int cellIndex) throws Exception {
		return getValueBy(rowList, rowIndex, cellIndex, String.class);
	}


	public static void deleteColumn(Sheet sheet, int columnToDeleteIndex) {
		for (int rId = 0; rId <= sheet.getLastRowNum(); rId++) {
			Row row = sheet.getRow(rId);
			for (int cID = columnToDeleteIndex; cID < row.getLastCellNum(); cID++) {
				Cell cOld = row.getCell(cID);
				if (cOld != null) {
					row.removeCell(cOld);
				}
				Cell cNext = row.getCell(cID + 1);
				if (cNext != null) {
					Cell cNew = row.createCell(cID, cNext.getCellType());
					cloneCell(cNew, cNext);
					//Set the column width only on the first row.
					//Other wise the second row will overwrite the original column width set previously.
					if (rId == 0) {
						sheet.setColumnWidth(cID, sheet.getColumnWidth(cID + 1));
					}
				}
			}
		}
	}

	public static void cloneCell(Cell cNew, Cell cOld) {
		cNew.setCellComment(cOld.getCellComment());
		cNew.setCellStyle(cOld.getCellStyle());

		if (CellType.BOOLEAN == cNew.getCellType()) {
			cNew.setCellValue(cOld.getBooleanCellValue());
		} else if (CellType.NUMERIC == cNew.getCellType()) {
			cNew.setCellValue(cOld.getNumericCellValue());
		} else if (CellType.STRING == cNew.getCellType()) {
			cNew.setCellValue(cOld.getStringCellValue());
		} else if (CellType.ERROR == cNew.getCellType()) {
			cNew.setCellValue(cOld.getErrorCellValue());
		} else if (CellType.FORMULA == cNew.getCellType()) {
			cNew.setCellValue(cOld.getCellFormula());
		}
	}

	public static byte[] deleteTemplateColumn(InputStream excelFileInput, String... key) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buff = new byte[1024 * 4];
		int n = 0;
		while (-1 != (n = excelFileInput.read(buff))) {
			output.write(buff, 0, n);
		}
		return deleteTemplateColumn(output.toByteArray(), 0, key);
	}

	public static byte[] deleteTemplateColumn(InputStream excelFileInput, int sheetIndex, String... keys) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buff = new byte[1024 * 4];
		int n = 0;
		while (-1 != (n = excelFileInput.read(buff))) {
			output.write(buff, 0, n);
		}
		return deleteTemplateColumn(output.toByteArray(), sheetIndex, keys);
	}

	public static byte[] deleteTemplateColumn(byte[] templateFile, String... key) throws Exception {
		return deleteTemplateColumn(templateFile, 0, key);
	}

	public static byte[] deleteTemplateColumn(byte[] templateFile, int sheetIndex, String... keys) throws Exception {
		Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(templateFile));
		Sheet sheet = workbook.getSheetAt(sheetIndex);

		List<ExcelRow> rowList = ExcelHelper.parseExcelRowList(new ByteArrayInputStream(templateFile));
		for (String kk : keys) {
			for (ExcelRow row : rowList) {
				List<ExcelCell> cellList = row.getCellList();
				for (ExcelCell cell : cellList) {
					if (cell.getValue().equals(kk) && sheetIndex == row.getSheetIndex()) {
						ExcelHelper.deleteColumn(sheet, cell.getIndex());
					}
				}
			}
		}

		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		workbook.write(byteOut);
		byteOut.flush();
		byteOut.close();
		return byteOut.toByteArray();
	}

	public static byte[] exportExcel(List<?> dataList) {
		Field[] fieldArr = dataList.get(0).getClass().getDeclaredFields();
		for (int i = 0; i < fieldArr.length; i++) {
			System.err.println(fieldArr[i].getName());
		}
		return null;
	}

	public static byte[] exportExcel(InputStream templeteStream, List<?> personList, String... delCellKey) throws Exception {
		return exportExcel(templeteStream, personList, null, delCellKey);
	}

	public static byte[] exportExcel(InputStream templeteStream, List<?> personList, CallBackCellStyle callBackCellStyle, String... delCellKey) throws Exception {
		ByteArrayOutputStream templeteOutput = new ByteArrayOutputStream();
		byte[] buff = new byte[1024 * 4];
		int n = 0;
		while (-1 != (n = templeteStream.read(buff))) {
			templeteOutput.write(buff, 0, n);
		}
		byte[] newTemPleteExcel = ExcelHelper.deleteTemplateColumn(templeteOutput.toByteArray(), delCellKey);
		return ExcelHelper.exportExcel(newTemPleteExcel, personList, callBackCellStyle);
	}
}
