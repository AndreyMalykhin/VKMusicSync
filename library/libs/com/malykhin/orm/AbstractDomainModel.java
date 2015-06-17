package com.malykhin.orm;

/**
 * 
 * @author Andrey Malykhin
 *
 */
abstract public class AbstractDomainModel {
	abstract public Object getIdentityField();
	abstract public AbstractDomainModel setIdentityField(Object identityField);
}
