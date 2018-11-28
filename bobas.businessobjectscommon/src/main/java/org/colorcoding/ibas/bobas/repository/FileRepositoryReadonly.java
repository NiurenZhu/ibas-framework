package org.colorcoding.ibas.bobas.repository;

import java.io.File;
import java.util.List;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.common.Conditions;
import org.colorcoding.ibas.bobas.common.ICondition;
import org.colorcoding.ibas.bobas.common.IConditions;
import org.colorcoding.ibas.bobas.common.ICriteria;
import org.colorcoding.ibas.bobas.common.IOperationResult;
import org.colorcoding.ibas.bobas.common.OperationResult;
import org.colorcoding.ibas.bobas.data.ArrayList;
import org.colorcoding.ibas.bobas.data.FileData;
import org.colorcoding.ibas.bobas.data.emYesNo;
import org.colorcoding.ibas.bobas.db.DataConvert;
import org.colorcoding.ibas.bobas.expression.FileJudgmentLink;
import org.colorcoding.ibas.bobas.expression.JudmentOperationException;
import org.colorcoding.ibas.bobas.i18n.I18N;
import org.colorcoding.ibas.bobas.message.Logger;

/**
 * 文件仓库只读
 * 
 * @author Niuren.Zhu
 *
 */
public class FileRepositoryReadonly implements IFileRepositoryReadonly {

	/**
	 * 检索条件项目：文件夹。如：documents，条件仅可等于，其他忽略。
	 */
	public static final String CRITERIA_CONDITION_ALIAS_FOLDER = "FileFolder";
	/**
	 * 检索条件项目：包含子文件夹。如： emYesNo.Yes，条件仅可等于，其他忽略。
	 */
	public static final String CRITERIA_CONDITION_ALIAS_INCLUDE_SUBFOLDER = "IncludeSubfolder";

	/**
	 * 检索条件项目：文件名称。如：ibas.*.jar，条件仅可等于，其他忽略。
	 */
	public static final String CRITERIA_CONDITION_ALIAS_FILE_NAME = FileJudgmentLink.CRITERIA_CONDITION_ALIAS_FILE_NAME;

	/**
	 * 检索条件项目：最后修改时间（文件时间）。如：1479965348，条件可等于，大小等于。
	 */
	public static final String CRITERIA_CONDITION_ALIAS_MODIFIED_TIME = FileJudgmentLink.CRITERIA_CONDITION_ALIAS_MODIFIED_TIME;

	private String repositoryFolder;

	@Override
	public String getRepositoryFolder() {
		if (this.repositoryFolder == null || this.repositoryFolder.isEmpty()) {
			String workFolder = MyConfiguration.getDataFolder() + File.separator + "filerepository";
			workFolder = MyConfiguration.getConfigValue(MyConfiguration.CONFIG_ITEM_FILE_REPOSITORY_FOLDER, workFolder);
			File file = new File(workFolder);
			if (!file.exists() && !file.isDirectory()) {
				file.mkdirs();
			}
			this.repositoryFolder = file.getPath();
		}
		return this.repositoryFolder;
	}

	@Override
	public void setRepositoryFolder(String folder) {
		this.repositoryFolder = folder;
	}

	@Override
	public IOperationResult<FileData> fetch(ICriteria criteria) {
		try {
			return new OperationResult<FileData>().addResultObjects(this.searchFiles(criteria));
		} catch (Exception e) {
			return new OperationResult<>(e);
		}
	}

	private List<FileData> searchFiles(ICriteria criteria) throws Exception {
		if (criteria == null || criteria.getConditions().isEmpty()) {
			throw new Exception(I18N.prop("msg_bobas_invaild_criteria"));
		}
		String workFolder = this.getRepositoryFolder();
		boolean include = false;
		IConditions conditions = new Conditions();
		for (ICondition condition : criteria.clone().getConditions()) {
			if (CRITERIA_CONDITION_ALIAS_FOLDER.equals(condition.getAlias())) {
				// 文件夹条件
				if (condition.getAlias() == null || condition.getAlias().isEmpty())
					continue;
				workFolder = workFolder + File.separator + condition.getValue();
			} else if (CRITERIA_CONDITION_ALIAS_INCLUDE_SUBFOLDER.equals(condition.getAlias())) {
				// 包含子文件夹
				if (condition.getAlias() == null || condition.getAlias().isEmpty())
					continue;
				emYesNo value = emYesNo.NO;
				if (condition.getValue().length() > 1)
					value = emYesNo.valueOf(condition.getValue());
				else {
					value = (emYesNo) DataConvert.toEnumValue(emYesNo.class, condition.getValue());
				}
				include = value == emYesNo.YES ? true : false;
			} else {
				conditions.add(condition);
			}
		}
		// 检查文件夹内文件是否符合条件
		File folder = new File(workFolder);
		if (!folder.isDirectory() || !folder.exists()) {
			throw new Exception(
					I18N.prop("msg_bobas_not_found_folder", workFolder.replace(this.getRepositoryFolder(), ".")));
		}
		// 查询符合条件的文件
		List<File> files = this.searchFiles(folder, include, conditions);
		// 输出文件数据
		ArrayList<FileData> nFileDatas = new ArrayList<>();
		for (File file : files) {
			FileData fileData = new FileData();
			fileData.setFileName(file.getName());
			fileData.setLocation(file.getPath());
			nFileDatas.add(fileData);
			if (criteria.getResultCount() > 0 && nFileDatas.size() >= criteria.getResultCount()) {
				break;
			}
		}
		return nFileDatas;
	}

	/**
	 * 查询文件
	 * 
	 * @param folder     目录
	 * @param include    是否包含子目录
	 * @param conditions 条件
	 * @return 符合条件的文件数组
	 */
	private List<File> searchFiles(File folder, boolean include, IConditions conditions) {
		ArrayList<File> files = new ArrayList<>();
		FileJudgmentLink judgmentLinks = null;
		File[] folderFiles = folder.listFiles();
		if (folderFiles != null) {
			for (File file : folderFiles) {
				if (file.isDirectory() && include) {
					files.addAll(this.searchFiles(file, include, conditions));
				} else if (file.isFile()) {
					if (judgmentLinks == null) {
						judgmentLinks = new FileJudgmentLink();
						judgmentLinks.parsingConditions(conditions);
					}
					try {
						boolean match = judgmentLinks.judge(file);
						if (match) {
							files.add(file);
						}
					} catch (JudmentOperationException e) {
						Logger.log(e);
					}
				}
			}

		}
		return files;
	}

}
