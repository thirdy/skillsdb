package com.apd.skilldb.service;

import static org.apache.commons.lang.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.apd.skilldb.entity.Employee;
import com.apd.skilldb.entity.EmployeeSkill;
import com.apd.skilldb.entity.Skill;
import com.apd.skilldb.repository.EmployeeRepository;
import com.apd.skilldb.repository.SkillRepository;

import lombok.Setter;

/**
 * Imports from the xlsx file that contains multiple employees.
 */
@Service
@Setter
public class ImportGroupService {
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private EmployeeRepository employeeRepository;
	
	@Autowired
	private SkillRepository skillRepository;

	public void parseAndSave(InputStream inputStream, String fileName) throws ImportServiceException {
		try {
			logger.info("Importing file: " + fileName);
			List<Employee> employees = parse(inputStream);
			for (Employee employee : employees) {
				employeeRepository.save(employee);
			}
		} catch (IOException e) {
			throw new ImportServiceException(fileName, e);
		}
	}
	
	List<Employee> parse(InputStream inputStream) throws IOException {
		List<Employee> employees = new LinkedList<>();
		
		try(Workbook workbook = new XSSFWorkbook(inputStream)) {
			
			Sheet listSheet = workbook.getSheet("List");
			listSheet = listSheet == null ? workbook.getSheet("Technology") : listSheet;
			employees = parseEmployeesFromListSheet(listSheet);
			employees.stream()
				.filter(e -> isNotBlank(e.getSheetName()))
				.forEach(e -> {
					Sheet skillSheet = workbook.getSheet(e.getSheetName());
					if (skillSheet == null) {
						logger.error("Failed to find Skill Sheet: " + e.getSheetName());
					}
					List<EmployeeSkill> empSkills = parseSkillSheet(skillSheet);
					e.setSkills(empSkills);
					empSkills.forEach(es -> es.setEmployee(e));
				});
		}
		return employees;
	}
	
	private List<EmployeeSkill> parseSkillSheet(Sheet sheet) {
		List<Row> rows = sheetToRows(sheet);
		List<Row> skillRows = determineSkillRows(rows);
		List<EmployeeSkill> empSkills = skillRows
				.stream()
				.map(sRow -> {
					String skillCategory = cellVal(sRow, 0);
					String skillName = cellVal(sRow, 1);
					String yrsExp = cellVal(sRow, 2);
					String level = cellVal(sRow, 3);
					boolean certified = equalsIgnoreCase(cellVal(sRow, 3), "Yes");
					
					Skill skill = findOrSaveSkill(skillCategory, skillName);
					EmployeeSkill employeeSkill = new EmployeeSkill(yrsExp, level, certified);
					employeeSkill.setSkill(skill);
					return employeeSkill;
				})
				.collect(Collectors.toList());
		return empSkills;
	}

	private Skill findOrSaveSkill(String skillCategory, String skillName) {
		Skill skill = null;
		
		List<Skill> skills = skillRepository.findBySkillName(skillName);
		
		if (skills.size() == 0) {
			skill = skillRepository.save(new Skill(skillCategory, skillName));
		} else {
			skill = skills.get(0);
		}
		
		return skill;
	}

	private List<Row> sheetToRows(Sheet sheet) {
		Iterator<Row> iterator = sheet.iterator();
		List<Row> rows = new ArrayList<>(200);
		iterator.forEachRemaining(rows::add);
		return rows;
	}

	private List<Employee> parseEmployeesFromListSheet(Sheet listSheet) {
		List<Row> rows = sheetToRows(listSheet);
		
		rows = rows.stream()
				.filter(r -> r.getRowNum() != 0)
				.filter(r -> isNotBlank(cellVal(r, 0)))
				.collect(Collectors.toList());
		
		List<Employee> employees = rows
				.stream()
				.map(r -> {
					Employee e = new Employee();
					e.setEmployeeId(cellVal(r, 0));
					e.setFirstName(cellVal(r, 2));
					e.setLastName(cellVal(r, 3));
					e.setRole(cellVal(r, 4));
					e.setEmail(cellVal(r, 5));
					e.setGender(cellVal(r, 8));
					e.setCountry(cellVal(r, 9));
					e.setDivision(cellVal(r, 10));
					e.setSheetName(cellVal(r, 12));
					return e;
				})
				.collect(Collectors.toList()); 
		
		return employees;
	}

	private List<Row> determineSkillRows(List<Row> rows) {
		List<Row> skillRows = new ArrayList<>();
		List<Row> rowsAfter4 = rows.subList(4, rows.size() - 1);
		String endRowMarker = "If we have missed any skills you have and you think it might be relevant to APD - you are highly encouraged to list it down";
		for (Row skillRow : rowsAfter4) {
			String cat = cellVal(skillRow, 0);
			String yrsExp = cellVal(skillRow, 2);
			if (equalsIgnoreCase(endRowMarker, cat) || isBlank(cat)) {
				break;
			}
			if (isNotBlank(yrsExp)) {
				skillRows.add(skillRow);
			}
		}
		return skillRows;
	}

	private String cellVal(Row row, int j) {
		Cell cell = row.getCell(j);
		return cell != null ? cell.getStringCellValue() : "";
	}

	public static class ImportServiceException extends Exception {
		private static final long serialVersionUID = 1L;

		public ImportServiceException(String fileName, Throwable t) {
			super("Failed to import file: " + fileName + ", cause: " + t.getMessage(), t);
		}
	}
}
