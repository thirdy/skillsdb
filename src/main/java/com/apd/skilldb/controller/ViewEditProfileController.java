package com.apd.skilldb.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;

import com.apd.skilldb.common.HibernateUtils;
import com.apd.skilldb.entity.Employee;
import com.apd.skilldb.entity.EmployeeSkill;
import com.apd.skilldb.entity.Skill;
import com.apd.skilldb.service.EmployeeService;
import com.apd.skilldb.service.SkillService;
import com.apd.skilldb.util.ExportUtil;

/**
 * @author alepasana
 *
 */
@Getter
@Setter
@ManagedBean
@SessionScoped
public class ViewEditProfileController {

	private Employee employee;
	private List<EmployeeSkill> allSkills;
	private List<EmployeeSkill> skills;
	private boolean isDeactivatedProfileMessageAdded = false;
	
	@ManagedProperty("#{skillService}")
	private SkillService skillService;
	
	@ManagedProperty("#{employeeService}")
	private EmployeeService employeeService;
	
	private String employeeId;	
	private String closable;
	
	@PostConstruct
	public void loadSkills(){
		allSkills = new ArrayList<EmployeeSkill>();
		List<Skill> skillList = skillService.findAll();
		
		if(skillList != null){
			for(Skill skill : skillList){
				EmployeeSkill empSkill = new EmployeeSkill();
				empSkill.setSkill(skill);
				allSkills.add(empSkill);
			}
		}
	}
	
	private void initializeEditSkills(){
		skills = new ArrayList<EmployeeSkill>();
		EmployeeSkill editSkill = null;
		for(int i = 0 ; i < allSkills.size(); i++){
			EmployeeSkill empSkill = allSkills.get(i);
			editSkill = new EmployeeSkill();
			
			Predicate predicate = HibernateUtils.attributePredicateFactory(EmployeeSkill.class, "skill.id", empSkill.getSkill().getId());
			@SuppressWarnings("unchecked")
			List<EmployeeSkill> selectedList = (List<EmployeeSkill>) CollectionUtils.select(employee.getSkills(), predicate);
			
			if(selectedList.size() > 0){
				BeanUtils.copyProperties(selectedList.get(0), editSkill);
			}else{
				BeanUtils.copyProperties(empSkill, editSkill);
			}
			
			editSkill.setEmployee(employee);
			skills.add(editSkill);
		}
	}
	
	public Employee getEmployee(){
		if(employee == null || !employee.getEmployeeId().equalsIgnoreCase(employeeId)){
			employee = employeeService.find(employeeId);
		}	
		
		if(employee.getIsActive() == 0 && !isDeactivatedProfileMessageAdded){
			addMessage("This profile has been deactivated and will no longer appear in the search skills page.");
			isDeactivatedProfileMessageAdded = true;
		}
			
		return employee;
	}
	
	private void addMessage(String message) {
		FacesContext.getCurrentInstance().addMessage
			(null, new FacesMessage(FacesMessage.SEVERITY_INFO, message, null));
	}
	
	public String edit(){	
		loadSkills();
		initializeEditSkills();	
		return "editprofile?faces-redirect=true&closable=" + closable;
	}
			
	public String save(){		
		List<EmployeeSkill> empSkills = new ArrayList<EmployeeSkill>();
		
		for(EmployeeSkill empSkill : skills){
			if(StringUtils.isNotBlank(empSkill.getYearsOfExperience()) || StringUtils.isNotBlank(empSkill.getLevel())){
				
				if(empSkill.getIsNewSkill() != null &&  empSkill.getIsNewSkill()){
					Skill newSkill = skillService.save(empSkill.getSkill());
					empSkill.getSkill().setId(newSkill.getId());
				}
				empSkills.add(empSkill);
			}
		}
		
		employee.setSkills(empSkills);	
		employee.setDateModified(new Date());
		employeeService.save(employee);	
		
		return "viewprofile?faces-redirect=true&closable=" + closable;
	}
	
	
	public String cancelEdit(){				
		return "viewprofile?faces-redirect=true&closable=" + closable;
	}
	
	public String deactivate(){		
		employee.setIsActive(0);
		employee.setDateModified(new Date());
		employeeService.save(employee);
		isDeactivatedProfileMessageAdded = false;
				
		return "viewprofile?faces-redirect=true&closable=" + closable;
	}
	
	public String activate(){		
		employee.setIsActive(1);
		employee.setDateModified(new Date());
		employeeService.save(employee);
		
		return "viewprofile?faces-redirect=true&closable=" + closable;
	}
	
	public String export() {
	    try {

	        String filename = employee.getFirstName() + "-" + employee.getLastName() + "_profile.csv";

	        FacesContext fc = FacesContext.getCurrentInstance();
	        HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();

	        response.reset();
	        response.setContentType("text/comma-separated-values");
	        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

	        OutputStream output = response.getOutputStream();
	        ExportUtil.exportProfile(employee, output);

	        output.flush();
	        output.close();

	        fc.responseComplete();

	    } catch (IOException e) {
	    	e.printStackTrace();
	    }
	    
	    return "";
	}	
	
	public void setEmployeeId(String employeeId, String closable){
		this.employeeId = employeeId;
		this.closable = closable;
	}
	
	
	public String newSkill(){
		EmployeeSkill empSkill = new EmployeeSkill();
		empSkill.setEmployee(employee);
		empSkill.setIsNewSkill(true);
		
		Skill skill = new Skill();
		empSkill.setSkill(skill);
		skills.add(empSkill);

		return "editprofile?faces-redirect=false&closable=" + closable;
	}
	
}
