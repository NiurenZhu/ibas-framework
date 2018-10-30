package org.colorcoding.ibas.bobas.approval;

import org.colorcoding.ibas.bobas.bo.IBusinessObject;
import org.colorcoding.ibas.bobas.common.ICriteria;
import org.colorcoding.ibas.bobas.common.OperationResult;
import org.colorcoding.ibas.bobas.core.BOFactory;
import org.colorcoding.ibas.bobas.core.IBORepository;
import org.colorcoding.ibas.bobas.core.RepositoryException;
import org.colorcoding.ibas.bobas.i18n.I18N;
import org.colorcoding.ibas.bobas.repository.BORepositoryLogicService;

/**
 * 审批流程专用业务仓库
 * 
 * @author Niuren.Zhu
 *
 */
class ApprovalProcessRepository extends BORepositoryLogicService {

	public ApprovalProcessRepository() {
		this.setCheckApprovalProcess(false);// 此业务仓库不检查审批流程
		this.setCheckPeriods(false);// 不检查期间
	}

	public ApprovalProcessRepository(IBORepository repository) {
		this();
		super.setRepository(repository);
	}

	public boolean openRepository() throws RepositoryException {
		return super.openRepository();
	}

	public void closeRepository() throws RepositoryException {
		super.closeRepository();
	}

	public boolean beginTransaction() throws RepositoryException {
		return super.beginTransaction();
	}

	public void rollbackTransaction() throws RepositoryException {
		super.rollbackTransaction();
	}

	public void commitTransaction() throws RepositoryException {
		super.commitTransaction();
	}

	/**
	 * 查询对象（涉及BOCode解析需要提前加载class）
	 * 
	 * @param criteria 条件，需要指定BOCode
	 * @return
	 */
	public <P extends IBusinessObject> OperationResult<P> fetch(ICriteria criteria) {
		try {
			// 加载命名空间的类
			Class<?> boClass = BOFactory.create().getClass(criteria.getBusinessObject());
			if (boClass == null) {
				throw new ClassNotFoundException(
						I18N.prop("msg_bobas_not_found_bo_class", criteria.getBusinessObject()));
			}
			@SuppressWarnings("unchecked")
			Class<P> boType = (Class<P>) boClass;
			String token = this.getCurrentUser().getToken();
			return super.fetch(criteria, token, boType);
		} catch (Exception e) {
			return new OperationResult<>(e);
		}
	}

	/**
	 * 保存对象
	 * 
	 * @param bo 业务对象
	 * @return
	 */
	public <P extends IBusinessObject> OperationResult<P> saveData(P bo) {
		String token = this.getCurrentUser().getToken();
		return super.save(bo, token);
	}
}
